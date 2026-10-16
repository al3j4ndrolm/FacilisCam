package io.github.al3j4ndrolm.faciliscam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.max

internal object BitmapDecoder {

    fun decode(context: Context, uri: Uri, maxSize: Int): Bitmap {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Can't decode $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
        }
        val sampled = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Can't decode $uri")

        val orientation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = orientationMatrix(orientation)
        val longest = max(sampled.width, sampled.height)
        if (longest > maxSize) {
            val scale = maxSize.toFloat() / longest
            matrix.postScale(scale, scale)
        }
        if (matrix.isIdentity) return sampled

        val result = Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
        if (result !== sampled) sampled.recycle()
        return result
    }

    /**
     * Largest power of two that keeps the longest side at or above [maxSize], so the final
     * downscale is done with filtering instead of by the decoder.
     */
    fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
        val longest = max(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxSize) sample *= 2
        return sample
    }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }
}
