package io.github.al3j4ndrolm.faciliscam

import android.content.Context
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Reads codes from a camera preview with ML Kit. Used by both the full-screen scanner and the
 * Compose [Scanner]. [onRead] gets each confirmed code and where it is in the preview.
 */
internal class CodeReader(formats: Set<Format>, private val onRead: (Code, Rect?) -> Unit) {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().run {
            val codes = formats.ifEmpty { Format.All }.map { it.mlKit }
            setBarcodeFormats(codes.first(), *codes.drop(1).toIntArray())
            build()
        }
    )
    private val confirmer = Confirmer()

    /** Starts reading frames from [controller]. Its preview must be shown in a PreviewView. */
    fun attach(context: Context, controller: LifecycleCameraController) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val barcodes = result.getValue(scanner).orEmpty()
                    .filter { it.rawValue != null && it.format.toFormat() != null }
                // Several codes in view: take the biggest, which is the one the user is pointing at.
                val barcode = barcodes.maxByOrNull { box -> box.boundingBox?.let { it.width() * it.height() } ?: 0 }
                if (barcode == null) {
                    confirmer.reset()
                    return@MlKitAnalyzer
                }
                val format = barcode.format.toFormat()!!
                val value = barcode.rawValue!!
                if (confirmer.offer(value, format)) onRead(Code.parse(value, format), barcode.boundingBox)
            },
        )
    }

    fun close() = scanner.close()
}

/**
 * Accepts a code once it has been seen enough frames in a row. One-line barcodes have weak
 * checksums and can be misread from a single blurry frame; 2D codes have error correction.
 */
internal class Confirmer {
    private var last: Pair<String, Format>? = null
    private var count = 0

    fun offer(value: String, format: Format): Boolean {
        val current = value to format
        count = if (current == last) count + 1 else 1
        last = current
        return count == if (format.isBarcode) 2 else 1
    }

    fun reset() {
        last = null
        count = 0
    }
}

private val Format.mlKit: Int
    get() = when (this) {
        Format.QR -> Barcode.FORMAT_QR_CODE
        Format.DataMatrix -> Barcode.FORMAT_DATA_MATRIX
        Format.Aztec -> Barcode.FORMAT_AZTEC
        Format.PDF417 -> Barcode.FORMAT_PDF417
        Format.EAN13 -> Barcode.FORMAT_EAN_13
        Format.EAN8 -> Barcode.FORMAT_EAN_8
        Format.UPCA -> Barcode.FORMAT_UPC_A
        Format.UPCE -> Barcode.FORMAT_UPC_E
        Format.Code128 -> Barcode.FORMAT_CODE_128
        Format.Code39 -> Barcode.FORMAT_CODE_39
        Format.Code93 -> Barcode.FORMAT_CODE_93
        Format.Codabar -> Barcode.FORMAT_CODABAR
        Format.ITF -> Barcode.FORMAT_ITF
    }

private fun Int.toFormat(): Format? = Format.entries.firstOrNull { it.mlKit == this }
