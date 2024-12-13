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

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.round

val downscalingFilterMode = FilterMipmap(
    filterMode =  FilterMode.LINEAR,
    mipmapMode = MipmapMode.LINEAR
)

val sharpeningFilter = ImageFilter.makeMatrixConvolution(
    kernelW = 3,
    kernelH = 3,
    kernel = floatArrayOf(
        0f, -0.05f, 0f,
        -0.05f, 1.2f, -0.05f,
        0f, -0.05f, 0f
    ),
    gain = 1F,
    bias = 0F,
    offsetX = 1,
    offsetY = 1,
    tileMode = FilterTileMode.CLAMP,
    convolveAlpha = false,
    input = null,
    crop = null
)

@Suppress("MagicNumber")
fun Image.scale(longSidePx: Int): Image {

    val resizeFactor: Double =
        longSidePx / max(width.toDouble(), height.toDouble())

    val scaledWidth: Int = max(1, round((resizeFactor * width) + 0.3).toInt())
    val scaledHeight: Int = max(1, round((resizeFactor * height) + 0.3).toInt())

    val bitmap = Bitmap()

    bitmap.allocN32Pixels(scaledWidth, scaledHeight)

    this.scalePixels(bitmap.peekPixels()!!, downscalingFilterMode, false)

    val downscaledImage = Image.makeFromBitmap(bitmap)

    val surface = Surface.makeRasterN32Premul(
        scaledWidth,
        scaledHeight
    )

    surface.canvas.drawImage(
        image = downscaledImage,
        left = 0f,
        top = 0f,
        paint = Paint().apply {
            imageFilter = sharpeningFilter
        }
    )

    return surface.makeImageSnapshot()
}

fun Image.encodeToJpg(quality: Int): ByteArray {

    val data = encodeToData(EncodedImageFormat.JPEG, quality)

    requireNotNull(data) { "JPG encoding failed." }

    return data.bytes
}
