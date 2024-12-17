/*
 * Copyright 2024 Ashampoo GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ashampoo.imageproxy

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.Vips
import app.photofox.vipsffm.VipsError
import app.photofox.vipsffm.VipsOption
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.round

private const val SERVER_BANNER = "Ashampoo Image Proxy Service"
private const val AUTHORIZATION_HEADER = "Authorization"

private val validQualityRange = 10..100

private const val DEFAULT_LONG_SIDE_PX = 480
private const val DEFAULT_QUALITY = 90

private const val MAX_LONG_SIDE_PX = 2048

private const val DEFAULT_TARGET_FORMAT = ".jpg"

private val httpClient = HttpClient()

private val noRotate = VipsOption.Enum("no_rotate", 1)
private val stripMetadata = VipsOption.Enum("strip", 1)

fun Application.configureRouting() {
    routing {

        get("/") {

            val remoteUrl = call.request.header("RemoteUrl")

            if (remoteUrl == null) {
                call.respondText(SERVER_BANNER)
                return@get
            }

            val longSidePx = call.request.header("LongSidePx")
                ?.toIntOrNull()
                ?: DEFAULT_LONG_SIDE_PX

            val quality = call.request.header("Quality")
                ?.toIntOrNull()
                ?.coerceIn(validQualityRange)
                ?: DEFAULT_QUALITY

            val authToken = call.request.header(AUTHORIZATION_HEADER)

            if (longSidePx > MAX_LONG_SIDE_PX) {

                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "LongSidePx size must be lower than $MAX_LONG_SIDE_PX pixels: $longSidePx"
                )

                return@get
            }

            val response = httpClient.get(remoteUrl) {

                /* If set, pass the auth token on to the remote service */
                if (authToken != null)
                    header(AUTHORIZATION_HEADER, authToken)
            }

            /* If the remote URL requires authorization we forward it as is. */
            if (response.status == HttpStatusCode.Unauthorized) {
                call.respond(response.status, response.bodyAsText())
                return@get
            }

            if (!response.status.isSuccess()) {

                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Call to remove URL responded with ${response.status}"
                )

                return@get
            }

            val remoteBytes = response.bodyAsBytes()

            try {

                val thumbnailBytes = createThumbnailBytes(
                    originalBytes = remoteBytes,
                    longSidePx = longSidePx,
                    quality = quality
                )

                call.respondBytes(
                    bytes = thumbnailBytes,
                    contentType = ContentType.Image.JPEG,
                    status = HttpStatusCode.OK
                )

            } catch (ex: VipsError) {

                log.error("Error in image processing.", ex)

                call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error")

                return@get
            }
        }
    }
}

private suspend fun createThumbnailBytes(
    originalBytes: ByteArray,
    longSidePx: Int,
    quality: Int
): ByteArray {

    val deferred = CompletableDeferred<ByteArray>()

    withContext(Dispatchers.IO) {

        try {

            Vips.run { arena ->

                val sourceImage = VImage.newFromBytes(arena, originalBytes)

                val resizeFactor: Double =
                    longSidePx / max(sourceImage.width.toDouble(), sourceImage.height.toDouble())

                @Suppress("MagicNumber")
                val thumbnailWidth = max(1, round(resizeFactor * sourceImage.width + 0.3).toInt())

                val thumbnail = sourceImage.thumbnailImage(
                    thumbnailWidth,
                    noRotate
                )

                val outputStream = ByteArrayOutputStream()

                thumbnail.writeToStream(
                    outputStream,
                    DEFAULT_TARGET_FORMAT,
                    stripMetadata,
                    VipsOption.Enum("Q", quality)
                )

                val thumbnailBytes = outputStream.toByteArray()

                deferred.complete(thumbnailBytes)
            }

        } catch (ex: Exception) {
            deferred.completeExceptionally(ex)
        }
    }

    return deferred.await()
}
