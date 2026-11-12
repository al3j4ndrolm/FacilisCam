package io.github.al3j4ndrolm.faciliscam.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.al3j4ndrolm.faciliscam.Code
import io.github.al3j4ndrolm.faciliscam.Lens
import io.github.al3j4ndrolm.faciliscam.Photo
import io.github.al3j4ndrolm.faciliscam.PhotoBox
import io.github.al3j4ndrolm.faciliscam.PhotoImage
import io.github.al3j4ndrolm.faciliscam.SaveTo
import io.github.al3j4ndrolm.faciliscam.Scanner
import io.github.al3j4ndrolm.faciliscam.camera
import io.github.al3j4ndrolm.faciliscam.rememberPhoto
import io.github.al3j4ndrolm.faciliscam.scan

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { SampleScreen() }
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    var status by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("1. PhotoBox: the whole thing in one line")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoBox(Modifier.size(160.dp)) { photo -> status = "PhotoBox got ${photo.uri.lastPathSegment}" }
            PhotoBox(
                modifier = Modifier.size(160.dp),
                lens = Lens.Front,
                saveTo = SaveTo.Gallery,
                shape = CircleShape,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoBox(Modifier.size(60.dp), shape = CircleShape)
            PhotoBox(Modifier.size(40.dp), shape = CircleShape)
            PhotoBox(Modifier.size(width = 200.dp, height = 60.dp))
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Section("2. rememberPhoto + PhotoImage")
        val photo = rememberPhoto()
        PhotoImage(photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Crop)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photo.take() }) { Text("Take photo") }
            OutlinedButton(onClick = { photo.clear() }, enabled = photo.value != null) { Text("Clear") }
        }

        HorizontalDivider()
        Section("3. camera.open + PhotoImage")
        var image by rememberSaveable { mutableStateOf<Photo?>(null) }
        PhotoImage(image, Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Crop)
        Button(onClick = { camera.open { image = it } }) { Text("Take photo") }

        HorizontalDivider()
        Section("4. camera.scan: QR codes and barcodes")
        var scanned by rememberSaveable { mutableStateOf("") }
        Button(onClick = { camera.scan { code -> scanned = describe(code) } }) { Text("Scan a code") }
        if (scanned.isNotEmpty()) Text(scanned, modifier = Modifier.semantics { contentDescription = "scanned" })

        HorizontalDivider()
        Section("5. Scanner: keep scanning")
        var scanning by rememberSaveable { mutableStateOf(false) }
        var torch by rememberSaveable { mutableStateOf(false) }
        val codes = remember { mutableStateListOf<String>() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scanning = !scanning }) { Text(if (scanning) "Stop" else "Start scanner") }
            OutlinedButton(onClick = { torch = !torch }, enabled = scanning) { Text(if (torch) "Light off" else "Light on") }
        }
        if (scanning) {
            Scanner(
                onCode = { code -> codes.add(0, describe(code)) },
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
                torch = torch,
            )
        }
        codes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun describe(code: Code): String = when (code) {
    is Code.Url -> "Link: ${code.url}"
    is Code.Wifi -> "Wi-Fi: ${code.ssid} (${code.security ?: "open"})"
    is Code.Email -> "Email: ${code.address}"
    is Code.Phone -> "Phone: ${code.number}"
    is Code.Sms -> "SMS to ${code.number}: ${code.message.orEmpty()}"
    is Code.Location -> "Location: ${code.latitude}, ${code.longitude}"
    is Code.Contact -> "Contact: ${code.name} ${code.phones.joinToString()}"
    is Code.Product -> "Product ${code.format}: ${code.value}"
    is Code.Text -> "Text (${code.format}): ${code.value}"
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}
