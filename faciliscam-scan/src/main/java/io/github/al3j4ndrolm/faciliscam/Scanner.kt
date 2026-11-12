package io.github.al3j4ndrolm.faciliscam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A live scanner inside your own screen. Keeps scanning: [onCode] runs for every new code, so it
 * suits scanning many items in a row (inventory, tickets, attendance).
 *
 * ```
 * Scanner(
 *     modifier = Modifier.size(300.dp),
 *     onCode = { code -> items += code.value },
 * )
 * ```
 *
 * The same code isn't reported again while it stays in view (or within [repeatAfterMillis]).
 * Camera permission is requested automatically.
 *
 * @param formats the kinds of codes to look for. Fewer formats scan faster.
 * @param torch turns the flashlight on.
 * @param repeatAfterMillis how long before the same code can be reported again.
 */
@Composable
fun Scanner(
    onCode: (Code) -> Unit,
    modifier: Modifier = Modifier,
    formats: Set<Format> = Format.All,
    torch: Boolean = false,
    repeatAfterMillis: Long = 2_000,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val activity = LocalActivity.current
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        // Denied with "don't ask again": Android won't show the dialog anymore, so send the user to Settings.
        blocked = !it && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // Back from Settings with the permission granted.
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (granted) {
            ScannerPreview(onCode, formats, torch, repeatAfterMillis)
        } else {
            BasicText(
                text = stringResource(
                    if (blocked) R.string.faciliscam_permission_open_settings else R.string.faciliscam_permission_allow
                ),
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .clickable {
                        if (blocked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        } else {
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ScannerPreview(onCode: (Code) -> Unit, formats: Set<Format>, torch: Boolean, repeatAfterMillis: Long) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentRepeat by rememberUpdatedState(repeatAfterMillis)

    val controller = remember(lifecycleOwner) {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_ANALYSIS) }
    }
    val lastSeen = remember { mutableMapOf<String, Long>() }
    val reader = remember(formats) {
        CodeReader(formats) { code, _ ->
            val now = System.currentTimeMillis()
            val previous = lastSeen[code.value]
            lastSeen[code.value] = now
            if (previous == null || now - previous >= currentRepeat) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
                )
                currentOnCode(code)
            }
        }
    }

    DisposableEffect(controller, reader) {
        reader.attach(context, controller)
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            reader.close()
        }
    }
    LaunchedEffect(controller, torch) {
        controller.initializationFuture.addListener({ controller.enableTorch(torch) }, ContextCompat.getMainExecutor(context))
    }

    // A PreviewView wraps to 0x0, which never starts the camera: it must fill the Scanner.
    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}
