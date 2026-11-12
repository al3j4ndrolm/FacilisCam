package io.github.al3j4ndrolm.faciliscam

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.startup.Initializer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opens a full-screen scanner and closes it as soon as a QR code or barcode is read.
 * [onCode] runs once the user is back on your screen. Camera permission is requested automatically.
 *
 * ```
 * camera.scan { code ->
 *     println(code.value)
 * }
 * ```
 *
 * @param formats the kinds of codes to look for. Fewer formats scan faster.
 * @param onCancel runs if the user closes the scanner without scanning.
 * @param onError runs if the camera can't be used (no camera, already open).
 */
fun FacilisCam.scan(
    formats: Set<Format> = Format.All,
    onCancel: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    onCode: (Code) -> Unit,
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        ScanTracker.mainHandler.post { scan(formats, onCancel, onError, onCode) }
        return
    }
    if (!ScanSession.start(ScanSession.Request(onCode, onCancel, onError))) {
        onError(IllegalStateException("The scanner is already open."))
        return
    }
    val activity = ScanTracker.currentActivity
    val context = activity ?: ScanTracker.appContext
    val intent = ScanActivity.intent(context, formats)
    if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Coroutine version of [scan]. Returns the code, or `null` if the user closed the scanner.
 *
 * ```
 * val code = camera.scanCode() ?: return
 * ```
 */
suspend fun FacilisCam.scanCode(formats: Set<Format> = Format.All): Code? = suspendCancellableCoroutine { continuation ->
    scan(
        formats = formats,
        onCancel = { if (continuation.isActive) continuation.resume(null) },
        onError = { if (continuation.isActive) continuation.resumeWithException(it) },
        onCode = { if (continuation.isActive) continuation.resume(it) },
    )
}

/**
 * Holds the caller's callbacks while the scanner is open, and delivers the outcome once the
 * caller's screen is resumed again. Only touched from the main thread.
 */
internal object ScanSession {

    class Request(val onCode: (Code) -> Unit, val onCancel: () -> Unit, val onError: (Throwable) -> Unit)

    private var request: Request? = null
    private var open = false
    private var outcome: (() -> Unit)? = null

    fun start(newRequest: Request): Boolean {
        if (open) return false
        deliverPending()
        request = newRequest
        outcome = null
        open = true
        return true
    }

    fun scanned(code: Code) = finish { request?.onCode?.invoke(code) }

    fun cancelled() = finish { request?.onCancel?.invoke() }

    fun failed(error: Throwable) = finish { request?.onError?.invoke(error) }

    fun deliverPending() {
        val deliver = outcome ?: return
        outcome = null
        deliver()
        request = null
    }

    private fun finish(deliver: () -> Unit) {
        if (!open) return
        open = false
        outcome = deliver
    }
}

/** Knows the current screen, so `camera.scan { }` needs no Context, and delivers results on resume. */
internal object ScanTracker : Application.ActivityLifecycleCallbacks {

    val mainHandler = Handler(Looper.getMainLooper())
    private var appContextOrNull: Context? = null
    private var current: WeakReference<Activity>? = null

    val appContext: Context
        get() = appContextOrNull ?: error(
            "FacilisCam isn't initialized. Make sure androidx.startup's InitializationProvider " +
                "isn't removed from your merged manifest."
        )

    val currentActivity: Activity?
        get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun install(context: Context) {
        if (appContextOrNull != null) return
        val app = context.applicationContext as Application
        appContextOrNull = app
        app.registerActivityLifecycleCallbacks(this)
    }

    private fun track(activity: Activity) {
        if (activity !is ScanActivity) current = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = track(activity)

    override fun onActivityStarted(activity: Activity) = track(activity)

    override fun onActivityResumed(activity: Activity) {
        track(activity)
        if (activity !is ScanActivity) mainHandler.post { ScanSession.deliverPending() }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
}

internal class FacilisCamScanInitializer : Initializer<Unit> {
    override fun create(context: Context) = ScanTracker.install(context)

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
