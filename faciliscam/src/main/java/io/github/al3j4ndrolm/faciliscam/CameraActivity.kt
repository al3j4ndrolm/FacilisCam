package io.github.al3j4ndrolm.faciliscam

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding

/** The full-screen camera opened by [FacilisCam.open]. Internal: apps never start it directly. */
internal class CameraActivity : ComponentActivity() {

    private lateinit var saveTo: SaveTo
    private lateinit var lens: Lens
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private var controller: LifecycleCameraController? = null
    private var resultSent = false

    private lateinit var previewView: PreviewView
    private lateinit var captureFlash: View
    private lateinit var closeButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var flipButton: ImageButton
    private lateinit var shutterButton: ImageButton
    private lateinit var permissionPanel: View
    private lateinit var permissionAction: Button

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermissions()) startCamera() else showPermissionPanel()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.faciliscam_activity_camera)

        saveTo = SaveTo.valueOf(intent.getStringExtra(EXTRA_SAVE_TO) ?: SaveTo.Cache.name)
        lens = Lens.valueOf(savedInstanceState?.getString(STATE_LENS) ?: intent.getStringExtra(EXTRA_LENS) ?: Lens.Back.name)
        flashMode = savedInstanceState?.getInt(STATE_FLASH) ?: ImageCapture.FLASH_MODE_OFF

        bindViews()

        when {
            hasPermissions() -> startCamera()
            savedInstanceState == null -> permissionLauncher.launch(requiredPermissions())
            else -> showPermissionPanel()
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission in system settings.
        if (permissionPanel.isVisible && hasPermissions()) startCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LENS, lens.name)
        outState.putInt(STATE_FLASH, flashMode)
    }

    override fun onPause() {
        super.onPause()
        // Report the cancel here, not in onDestroy: the caller resumes before this activity is destroyed.
        if (isFinishing) sendCancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) sendCancel()
    }

    private fun bindViews() {
        previewView = findViewById(R.id.faciliscam_preview)
        captureFlash = findViewById(R.id.faciliscam_capture_flash)
        closeButton = findViewById(R.id.faciliscam_close)
        flashButton = findViewById(R.id.faciliscam_flash)
        flipButton = findViewById(R.id.faciliscam_flip)
        shutterButton = findViewById(R.id.faciliscam_shutter)
        permissionPanel = findViewById(R.id.faciliscam_permission_panel)
        permissionAction = findViewById(R.id.faciliscam_permission_action)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.faciliscam_controls)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        closeButton.setOnClickListener { finish() }
        flashButton.setOnClickListener { cycleFlash() }
        flipButton.setOnClickListener { flipLens() }
        shutterButton.setOnClickListener { takePhoto() }
        permissionAction.setOnClickListener { onPermissionAction() }
        updateFlashIcon()
    }

    // region Camera

    private fun startCamera() {
        permissionPanel.isVisible = false
        shutterButton.isVisible = true
        setControlsEnabled(true)
        if (controller != null) return

        val cameraController = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            cameraSelector = lens.selector
            imageCaptureFlashMode = flashMode
        }
        controller = cameraController
        previewView.controller = cameraController
        cameraController.bindToLifecycle(this)

        cameraController.initializationFuture.addListener({
            if (isDestroyed) return@addListener
            val hasBack = cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            when {
                !hasBack && !hasFront -> {
                    sendError(IllegalStateException("This device has no camera."))
                    return@addListener
                }
                !cameraController.hasCamera(lens.selector) -> switchTo(if (hasBack) Lens.Back else Lens.Front)
            }
            flipButton.isVisible = hasBack && hasFront
            updateFlashVisibility()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipLens() = switchTo(if (lens == Lens.Back) Lens.Front else Lens.Back)

    private fun switchTo(newLens: Lens) {
        val cameraController = controller ?: return
        lens = newLens
        cameraController.cameraSelector = newLens.selector
        updateFlashVisibility()
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        controller?.imageCaptureFlashMode = flashMode
        updateFlashIcon()
    }

    private fun updateFlashIcon() {
        val (icon, label) = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.faciliscam_ic_flash_auto to R.string.faciliscam_flash_auto
            ImageCapture.FLASH_MODE_ON -> R.drawable.faciliscam_ic_flash_on to R.string.faciliscam_flash_on
            else -> R.drawable.faciliscam_ic_flash_off to R.string.faciliscam_flash_off
        }
        flashButton.setImageResource(icon)
        flashButton.contentDescription = getString(label)
    }

    private fun updateFlashVisibility() {
        flashButton.isVisible = controller?.cameraInfo?.hasFlashUnit() == true
    }

    private fun takePhoto() {
        val cameraController = controller ?: return
        setControlsEnabled(false)
        captureFlash.alpha = 1f
        captureFlash.animate().alpha(0f).setDuration(250).start()

        val storage = PhotoStorage.create(this, saveTo, mirror = lens == Lens.Front)
        cameraController.takePicture(
            storage.outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        sendPhoto(storage.toPhoto(this@CameraActivity, output.savedUri))
                    } catch (e: Exception) {
                        sendError(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) = sendError(exception)
            },
        )
    }

    private fun setControlsEnabled(enabled: Boolean) {
        listOf(shutterButton, flipButton, flashButton).forEach { it.isEnabled = enabled }
    }

    // endregion

    // region Permissions

    private fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (saveTo == SaveTo.Gallery && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /** True once the user has denied with "don't ask again" (or denied twice). */
    private fun permanentlyDenied() = requiredPermissions().any {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(it)
    }

    private fun showPermissionPanel() {
        permissionPanel.isVisible = true
        shutterButton.isVisible = false
        setControlsEnabled(false)
        findViewById<TextView>(R.id.faciliscam_permission_message).setText(R.string.faciliscam_permission_message)
        permissionAction.setText(
            if (permanentlyDenied()) R.string.faciliscam_permission_open_settings else R.string.faciliscam_permission_allow
        )
    }

    private fun onPermissionAction() {
        if (permanentlyDenied()) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    // endregion

    private fun sendPhoto(photo: Photo) {
        resultSent = true
        CameraSession.photoTaken(photo)
        finish()
    }

    private fun sendCancel() {
        if (resultSent) return
        resultSent = true
        CameraSession.cancelled()
    }

    private fun sendError(error: Throwable) {
        resultSent = true
        CameraSession.failed(error)
        finish()
    }

    private val Lens.selector: CameraSelector
        get() = if (this == Lens.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    companion object {
        private const val EXTRA_LENS = "faciliscam.lens"
        private const val EXTRA_SAVE_TO = "faciliscam.save_to"
        private const val STATE_LENS = "faciliscam.state.lens"
        private const val STATE_FLASH = "faciliscam.state.flash"

        fun intent(context: Context, lens: Lens, saveTo: SaveTo) =
            Intent(context, CameraActivity::class.java)
                .putExtra(EXTRA_LENS, lens.name)
                .putExtra(EXTRA_SAVE_TO, saveTo.name)
    }
}
