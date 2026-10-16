package io.github.al3j4ndrolm.faciliscam

/**
 * Holds the caller's callbacks while the camera screen is open, and delivers the outcome once the
 * caller's screen is resumed again (so callers can safely navigate or commit fragments in them).
 *
 * Only touched from the main thread.
 */
internal object CameraSession {

    class Request(
        val onPhoto: (Photo) -> Unit,
        val onCancel: () -> Unit,
        val onError: (Throwable) -> Unit,
    )

    private sealed interface Outcome {
        class Taken(val photo: Photo) : Outcome
        object Cancelled : Outcome
        class Failed(val error: Throwable) : Outcome
    }

    private var request: Request? = null
    private var cameraOpen = false
    private var outcome: Outcome? = null

    /** Returns false if the camera is already open. */
    fun start(newRequest: Request): Boolean {
        if (cameraOpen) return false
        // A previous result that hasn't reached its caller yet must not be dropped.
        deliverPending()
        request = newRequest
        outcome = null
        cameraOpen = true
        return true
    }

    fun photoTaken(photo: Photo) = finish(Outcome.Taken(photo))

    fun cancelled() = finish(Outcome.Cancelled)

    fun failed(error: Throwable) = finish(Outcome.Failed(error))

    /** Called whenever a non-camera activity resumes. */
    fun deliverPending() {
        val result = outcome ?: return
        val callbacks = request ?: return
        outcome = null
        request = null
        when (result) {
            is Outcome.Taken -> callbacks.onPhoto(result.photo)
            is Outcome.Cancelled -> callbacks.onCancel()
            is Outcome.Failed -> callbacks.onError(result.error)
        }
    }

    private fun finish(result: Outcome) {
        if (!cameraOpen) return
        cameraOpen = false
        outcome = result
    }
}
