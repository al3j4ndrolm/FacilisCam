package io.github.al3j4ndrolm.faciliscam

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.startup.Initializer
import java.lang.ref.WeakReference

/**
 * Knows the app's current activity, so `camera.open { }` works without passing a Context.
 * Installed automatically at app start by [FacilisCamInitializer].
 */
internal object ActivityTracker : Application.ActivityLifecycleCallbacks {

    private var appContextOrNull: Context? = null
    private var current: WeakReference<Activity>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
        if (activity !is CameraActivity) current = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = track(activity)

    override fun onActivityStarted(activity: Activity) = track(activity)

    override fun onActivityResumed(activity: Activity) {
        track(activity)
        if (activity !is CameraActivity) {
            // Post so callbacks run after the activity has fully resumed.
            mainHandler.post { CameraSession.deliverPending() }
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
}

internal class FacilisCamInitializer : Initializer<Unit> {
    override fun create(context: Context) = ActivityTracker.install(context)

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
