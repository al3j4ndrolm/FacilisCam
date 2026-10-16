package io.github.al3j4ndrolm.faciliscam

import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import java.io.File

/**
 * A photo taken with [FacilisCam.open].
 *
 * It's [Parcelable], so it can be kept in `rememberSaveable`, a `SavedStateHandle` or a `Bundle`.
 *
 * @property uri a `content://` Uri. Works with `ImageView.setImageURI`, Coil, Glide, or sharing
 *   with other apps.
 * @property file the saved file when saved with [SaveTo.Cache], `null` for [SaveTo.Gallery].
 */
class Photo internal constructor(
    val uri: Uri,
    val file: File?,
) : Parcelable {

    /**
     * Decodes the photo, rotated the right way up, with its longest side at most [maxSize] pixels.
     *
     * This reads from disk, so for large sizes prefer calling it off the main thread.
     */
    fun bitmap(maxSize: Int = 2048): Bitmap =
        BitmapDecoder.decode(ActivityTracker.appContext, uri, maxSize)

    override fun equals(other: Any?): Boolean = other is Photo && other.uri == uri

    override fun hashCode(): Int = uri.hashCode()

    override fun toString(): String = "Photo(uri=$uri)"

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, flags)
        dest.writeString(file?.path)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Photo> = object : Parcelable.Creator<Photo> {
            override fun createFromParcel(source: Parcel): Photo {
                val uri = checkNotNull(ParcelCompat.readParcelable(source, Uri::class.java.classLoader, Uri::class.java))
                val file = source.readString()?.let(::File)
                return Photo(uri, file)
            }

            override fun newArray(size: Int): Array<Photo?> = arrayOfNulls(size)
        }
    }
}
