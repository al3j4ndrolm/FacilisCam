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
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.al3j4ndrolm.faciliscam.scan.R

/** The full-screen scanner opened by [FacilisCam.scan]. Internal: apps never start it directly. */
internal class ScanActivity : ComponentActivity() {

    private var controller: LifecycleCameraController? = null
    private var reader: CodeReader? = null
    private var torchOn = false
    private var resultSent = false

    private lateinit var previewView: PreviewView
    private lateinit var viewfinder: ViewfinderView
    private lateinit var torchButton: ImageButton
    private lateinit var hint: View
    private lateinit var permissionPanel: View
    private lateinit var permissionAction: Button

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionPanel()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.faciliscam_scan_activity)

        previewView = findViewById(R.id.faciliscam_scan_preview)
        viewfinder = findViewById(R.id.faciliscam_scan_viewfinder)
        torchButton = findViewById(R.id.faciliscam_scan_torch)
        hint = findViewById(R.id.faciliscam_scan_hint)
        permissionPanel = findViewById(R.id.faciliscam_scan_permission_panel)
        permissionAction = findViewById(R.id.faciliscam_scan_permission_action)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.faciliscam_scan_controls)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        findViewById<View>(R.id.faciliscam_scan_close).setOnClickListener { finish() }
        torchButton.setOnClickListener { toggleTorch() }
        permissionAction.setOnClickListener { onPermissionAction() }

        torchOn = savedInstanceState?.getBoolean(STATE_TORCH) ?: false
        when {
            hasPermission() -> startCamera()
            savedInstanceState == null -> permissionLauncher.launch(Manifest.permission.CAMERA)
            else -> showPermissionPanel()
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission in system settings.
        if (permissionPanel.isVisible && hasPermission()) startCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TORCH, torchOn)
    }

    override fun onPause() {
        super.onPause()
        // Report the cancel here, not in onDestroy: the caller resumes before this activity is destroyed.
        if (isFinishing) sendCancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        reader?.close()
        if (isFinishing) sendCancel()
    }

    private fun startCamera() {
        permissionPanel.isVisible = false
        hint.isVisible = true
        if (controller != null) return

        val formats = intent.getStringArrayExtra(EXTRA_FORMATS)?.map { Format.valueOf(it) }?.toSet() ?: Format.All
        val codeReader = CodeReader(formats) { code, box -> onCodeRead(code, box) }
        val cameraController = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
        reader = codeReader
        controller = cameraController
        previewView.controller = cameraController
        codeReader.attach(this, cameraController)
        cameraController.bindToLifecycle(this)

        cameraController.initializationFuture.addListener({
            if (isDestroyed) return@addListener
            if (!cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                if (cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    sendError(IllegalStateException("This device has no camera."))
                    return@addListener
                }
            }
            torchButton.isVisible = cameraController.cameraInfo?.hasFlashUnit() == true
            if (torchOn) cameraController.enableTorch(true)
            updateTorchIcon()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCodeRead(code: Code, box: android.graphics.Rect?) {
        if (resultSent) return
        resultSent = true
        controller?.clearImageAnalysisAnalyzer()
        viewfinder.showCode(box)
        hint.isVisible = false
        viewfinder.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        )
        // A short pause so the user sees which code was read.
        viewfinder.postDelayed({
            ScanSession.scanned(code)
            finish()
        }, 250)
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        controller?.enableTorch(torchOn)
        updateTorchIcon()
    }

    private fun updateTorchIcon() {
        torchButton.setImageResource(if (torchOn) R.drawable.faciliscam_scan_ic_torch_on else R.drawable.faciliscam_scan_ic_torch_off)
        torchButton.contentDescription = getString(if (torchOn) R.string.faciliscam_scan_torch_off else R.string.faciliscam_scan_torch_on)
    }

    // region Permission

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** True once the user has denied with "don't ask again" (or denied twice). */
    private fun permanentlyDenied() = !hasPermission() && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    private fun showPermissionPanel() {
        permissionPanel.isVisible = true
        hint.isVisible = false
        permissionAction.setText(
            if (permanentlyDenied()) io.github.al3j4ndrolm.faciliscam.R.string.faciliscam_permission_open_settings
            else io.github.al3j4ndrolm.faciliscam.R.string.faciliscam_permission_allow
        )
    }

    private fun onPermissionAction() {
        if (permanentlyDenied()) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // endregion

    private fun sendCancel() {
        if (resultSent) return
        resultSent = true
        ScanSession.cancelled()
    }

    private fun sendError(error: Throwable) {
        resultSent = true
        ScanSession.failed(error)
        finish()
    }

    companion object {
        private const val EXTRA_FORMATS = "faciliscam.scan.formats"
        private const val STATE_TORCH = "faciliscam.scan.state.torch"

        fun intent(context: Context, formats: Set<Format>) =
            Intent(context, ScanActivity::class.java)
                .putExtra(EXTRA_FORMATS, formats.map { it.name }.toTypedArray())
    }
}
