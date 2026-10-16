package io.github.al3j4ndrolm.faciliscam

import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The camera. Available everywhere (Activity, Fragment, Compose, ViewModel) with no setup:
 *
 * ```
 * camera.open { image ->
 *     imageView.setImageURI(image.uri)
 * }
 * ```
 */
val camera: FacilisCam get() = FacilisCam

/** Which camera to start with. The user can still switch while the camera is open. */
enum class Lens { Back, Front }

/** Where the photo is saved. */
enum class SaveTo {
    /** App-private cache. Nothing shows up in the user's gallery. */
    Cache,

    /** The user's gallery, under `Pictures/<your app name>`. */
    Gallery,
}

object FacilisCam {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Opens a full-screen camera. [onPhoto] runs with the taken photo once the user is back on
     * your screen. Camera permission is requested automatically.
     *
     * @param lens the camera to start with.
     * @param saveTo where to save the photo.
     * @param onCancel runs if the user closes the camera without taking a photo.
     * @param onError runs if the camera can't be used (no camera, capture failure, already open).
     */
    fun open(
        lens: Lens = Lens.Back,
        saveTo: SaveTo = SaveTo.Cache,
        onCancel: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onPhoto: (Photo) -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { open(lens, saveTo, onCancel, onError, onPhoto) }
            return
        }

        val request = CameraSession.Request(onPhoto, onCancel, onError)
        if (!CameraSession.start(request)) {
            onError(IllegalStateException("The camera is already open."))
            return
        }

        val activity = ActivityTracker.currentActivity
        val context = activity ?: ActivityTracker.appContext
        val intent = CameraActivity.intent(context, lens, saveTo)
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Coroutine version of [open]. Returns the photo, or `null` if the user closed the camera.
     *
     * ```
     * val image = camera.capture() ?: return
     * ```
     */
    suspend fun capture(
        lens: Lens = Lens.Back,
        saveTo: SaveTo = SaveTo.Cache,
    ): Photo? = suspendCancellableCoroutine { continuation ->
        open(
            lens = lens,
            saveTo = saveTo,
            onCancel = { if (continuation.isActive) continuation.resume(null) },
            onError = { if (continuation.isActive) continuation.resumeWithException(it) },
            onPhoto = { if (continuation.isActive) continuation.resume(it) },
        )
    }
}
