package io.github.al3j4ndrolm.faciliscam

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Shows a [Photo], decoded in the background, the right way up, and sized for the screen.
 * Shows nothing while [photo] is `null` or still loading.
 *
 * ```
 * PhotoImage(photo, Modifier.fillMaxWidth())
 * ```
 */
@Composable
fun PhotoImage(
    photo: Photo?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val window = LocalWindowInfo.current.containerSize
    val maxSize = max(window.width, window.height).coerceAtLeast(1)

    // Keeps showing the previous photo until the new one is decoded, so retakes don't flicker.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photo, maxSize) {
        value = photo?.let {
            withContext(Dispatchers.IO) { runCatching { it.bitmap(maxSize).asImageBitmap() }.getOrNull() }
        }
    }

    val image = bitmap
    if (image == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
        )
    }
}

/** Shows the photo held by a [PhotoState] from [rememberPhoto]. */
@Composable
fun PhotoImage(
    state: PhotoState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) = PhotoImage(state.value, modifier, contentDescription, contentScale, alignment)
