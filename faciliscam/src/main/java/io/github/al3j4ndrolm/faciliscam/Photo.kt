package io.github.al3j4ndrolm.faciliscam

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * A photo taken with [FacilisCam.open].
 *
 * @property uri a `content://` Uri. Works with `ImageView.setImageURI`, Coil, Glide, or sharing
 *   with other apps.
 * @property file the saved file when saved with [SaveTo.Cache], `null` for [SaveTo.Gallery].
 */
class Photo internal constructor(
    val uri: Uri,
    val file: File?,
) {

    /**
     * Decodes the photo, rotated the right way up, with its longest side at most [maxSize] pixels.
     *
     * This reads from disk, so for large sizes prefer calling it off the main thread.
     */
    fun bitmap(maxSize: Int = 2048): Bitmap =
        BitmapDecoder.decode(ActivityTracker.appContext, uri, maxSize)

    override fun toString(): String = "Photo(uri=$uri)"
}
