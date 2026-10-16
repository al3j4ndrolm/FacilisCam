package io.github.al3j4ndrolm.faciliscam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.al3j4ndrolm.faciliscam.compose.R

/**
 * A complete photo field: shows a "Take photo" placeholder, opens the camera when tapped, then
 * shows the photo. Tap again to retake.
 *
 * ```
 * PhotoBox(Modifier.size(240.dp))
 *
 * PhotoBox(Modifier.size(240.dp)) { photo -> upload(photo.uri) }
 * ```
 *
 * @param state holds the photo. Pass your own [rememberPhoto] to read or clear it.
 * @param onPhoto runs once for each new photo the user takes.
 */
@Composable
fun PhotoBox(
    modifier: Modifier = Modifier,
    state: PhotoState = rememberPhoto(),
    lens: Lens = Lens.Back,
    saveTo: SaveTo = SaveTo.Cache,
    shape: Shape = RoundedCornerShape(16.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable BoxScope.() -> Unit = { PhotoBoxDefaults.Placeholder() },
    onPhoto: (Photo) -> Unit = {},
) {
    val photo = state.value
    val currentOnPhoto by rememberUpdatedState(onPhoto)

    // Remembered across rotation so a restored photo isn't reported twice.
    var reportedUri by rememberSaveable { mutableStateOf(photo?.uri?.toString()) }
    LaunchedEffect(photo) {
        if (photo != null && photo.uri.toString() != reportedUri) {
            reportedUri = photo.uri.toString()
            currentOnPhoto(photo)
        }
    }

    val label = stringResource(
        if (photo == null) R.string.faciliscam_compose_take_photo else R.string.faciliscam_compose_retake_photo
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 160.dp, minHeight = 160.dp)
            .clip(shape)
            .background(PhotoBoxDefaults.BackgroundColor)
            .clickable(onClickLabel = label, role = Role.Button) { state.take(lens, saveTo) },
        contentAlignment = Alignment.Center,
    ) {
        if (photo == null) {
            placeholder()
        } else {
            PhotoImage(photo, Modifier.matchParentSize(), contentDescription = label, contentScale = contentScale)
        }
    }
}

object PhotoBoxDefaults {
    /** Neutral gray that reads on both light and dark backgrounds. */
    val BackgroundColor = Color(0x1F808080)
    val ContentColor = Color(0xFF808080)

    /** The default placeholder: a camera icon over "Take photo". */
    @Composable
    fun Placeholder(color: Color = ContentColor) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.faciliscam_compose_ic_camera),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(40.dp),
            )
            BasicText(
                text = stringResource(R.string.faciliscam_compose_take_photo),
                style = TextStyle(color = color, fontSize = 14.sp),
            )
        }
    }
}
