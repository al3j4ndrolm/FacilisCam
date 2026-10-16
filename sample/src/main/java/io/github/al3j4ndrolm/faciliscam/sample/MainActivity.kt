package io.github.al3j4ndrolm.faciliscam.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.al3j4ndrolm.faciliscam.Lens
import io.github.al3j4ndrolm.faciliscam.Photo
import io.github.al3j4ndrolm.faciliscam.SaveTo
import io.github.al3j4ndrolm.faciliscam.camera
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { SampleScreen() }
        }
    }
}

@Composable
private fun SampleScreen() {
    var photo by remember { mutableStateOf<Photo?>(null) }
    var status by remember { mutableStateOf("No photo yet") }
    val show = { image: Photo -> photo = image; status = "Got ${image.uri.lastPathSegment}" }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bitmap = remember(photo) { photo?.bitmap(maxSize = 1080)?.asImageBitmap() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Last photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(status)

        // The whole integration: one call.
        Button(onClick = { camera.open { image -> show(image) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Take photo")
        }

        OutlinedButton(
            onClick = {
                camera.open(
                    lens = Lens.Front,
                    saveTo = SaveTo.Gallery,
                    onCancel = { status = "Cancelled" },
                    onError = { status = "Error: ${it.message}" },
                ) { image -> show(image) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Selfie, saved to gallery")
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    val image = camera.capture()
                    if (image == null) status = "Cancelled" else show(image)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Coroutine version")
        }
    }
}
