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

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.skia.Image
import org.slf4j.LoggerFactory

private const val SERVER_BANNER = "Ashampoo Image Proxy Service"
private const val AUTHORIZATION_HEADER = "Authorization"

private val validQualityRange = 10..100

private const val DEFAULT_LONG_SIDE_PX = 480
private const val DEFAULT_QUALITY = 90

private val httpClient = HttpClient()

private val usageString = buildString {

    appendLine(SERVER_BANNER)
    appendLine()
    appendLine(
        "Usage: Send HTTP GET request with headers " +
            "'RemoteUrl' (mandatory), 'LongSidePx' (optional) & 'Quality' (optional) set."
    )
}

fun Application.configureRouting() {
    routing {

        get("/") {

            val remoteUrl = call.request.header("RemoteUrl")

            if (remoteUrl == null) {
                call.respondText(usageString)
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

            if (longSidePx > 2000) {

                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "LongSidePx size must be lower than 2000px: $longSidePx"
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

            val image = Image.makeFromEncoded(remoteBytes)

            val thumbnail = image.scale(longSidePx)

            val thumbnailBytes = thumbnail.encodeToJpg(quality)

            call.respondBytes(
                bytes = thumbnailBytes,
                contentType = ContentType.Image.JPEG,
                status = HttpStatusCode.OK
            )
        }
    }
}
