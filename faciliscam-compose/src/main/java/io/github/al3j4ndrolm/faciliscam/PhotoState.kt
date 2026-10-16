package io.github.al3j4ndrolm.faciliscam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Remembers a photo and the camera that takes it. Survives rotation and process recreation,
 * including a rotation while the camera is open.
 *
 * ```
 * val photo = rememberPhoto()
 *
 * Button(onClick = { photo.take() }) { Text("Take photo") }
 * PhotoImage(photo, Modifier.fillMaxWidth())
 * ```
 */
@Composable
fun rememberPhoto(initial: Photo? = null): PhotoState {
    val state = rememberSaveable(saver = PhotoState.Saver) { PhotoState(UUID.randomUUID().toString(), initial) }
    DisposableEffect(state) {
        state.attach()
        onDispose { state.detach() }
    }
    return state
}

@Stable
class PhotoState internal constructor(
    private val id: String,
    initial: Photo?,
) {
    /** The current photo, or `null` if none has been taken yet. */
    var value: Photo? by mutableStateOf(initial)

    /** Opens the camera; [value] is updated when the user takes a photo. */
    fun take(
        lens: Lens = Lens.Back,
        saveTo: SaveTo = SaveTo.Cache,
        onCancel: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        camera.open(lens, saveTo, onCancel, onError) { photo -> deliver(id, photo) }
    }

    /** Removes the current photo. */
    fun clear() {
        value = null
    }

    internal fun attach() {
        attached[id] = this
        pending.remove(id)?.let { value = it }
    }

    internal fun detach() {
        if (attached[id] === this) attached.remove(id)
    }

    internal companion object {
        /** The on-screen instance for each id. Only touched from the main thread. */
        private val attached = HashMap<String, PhotoState>()

        /**
         * Photos that arrived while no instance was on screen: the screen was recreated while
         * the camera was open (e.g. rotation) and its new UI hasn't been composed yet.
         */
        private val pending = HashMap<String, Photo>()

        private fun deliver(id: String, photo: Photo) {
            val target = attached[id]
            if (target != null) target.value = photo else pending[id] = photo
        }

        val Saver = listSaver<PhotoState, Any?>(
            save = { listOf(it.id, it.value) },
            restore = { PhotoState(it[0] as String, it[1] as Photo?) },
        )
    }
}
