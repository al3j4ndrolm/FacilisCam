package io.github.al3j4ndrolm.faciliscam

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where a capture is written, and how to turn the saved result into a [Photo]. */
internal class PhotoStorage private constructor(
    val outputOptions: ImageCapture.OutputFileOptions,
    private val file: File?,
) {

    fun toPhoto(context: Context, savedUri: Uri?): Photo {
        if (file != null) {
            val uri = FileProvider.getUriForFile(context, authority(context), file)
            return Photo(uri, file)
        }
        return Photo(checkNotNull(savedUri) { "CameraX didn't return a Uri for the saved photo" }, null)
    }

    companion object {

        fun create(context: Context, saveTo: SaveTo, mirror: Boolean): PhotoStorage {
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = mirror }
            val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

            return when (saveTo) {
                SaveTo.Cache -> {
                    val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
                    val file = File(dir, "$name.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
                    PhotoStorage(options, file)
                }

                SaveTo.Gallery -> {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/${galleryFolder(context)}")
                        }
                    }
                    val options = ImageCapture.OutputFileOptions.Builder(
                        context.contentResolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values,
                    ).setMetadata(metadata).build()
                    PhotoStorage(options, null)
                }
            }
        }

        private const val CACHE_DIR = "faciliscam"

        private fun authority(context: Context) = "${context.packageName}.faciliscam.provider"

        private fun galleryFolder(context: Context): String =
            context.applicationInfo.loadLabel(context.packageManager).toString()
                .replace(Regex("[^A-Za-z0-9 _-]"), "")
                .trim()
                .ifEmpty { "FacilisCam" }
    }
}
