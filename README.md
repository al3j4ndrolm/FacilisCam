# FacilisCam

The camera in one line. No permissions code, no CameraX setup, no `PreviewView`, no file providers.

```kotlin
camera.open { image ->
    imageView.setImageURI(image.uri)
}
```

That's the whole integration. It works the same from an Activity, a Fragment, Jetpack Compose or a ViewModel. You don't pass a `Context` and there's no `init()`.

## Install

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency:

```kotlin
implementation("com.github.al3j4ndrolm:FacilisCam:1.0.0")
```

Min SDK 23. You don't need to edit your manifest: the camera permission, the camera screen and the file provider are merged in automatically.

## Usage

```kotlin
import io.github.al3j4ndrolm.faciliscam.camera

camera.open { image -> /* use image.uri */ }
```

### Options

```kotlin
camera.open(
    lens = Lens.Front,            // Lens.Back (default) or Lens.Front
    saveTo = SaveTo.Gallery,      // SaveTo.Cache (default) or SaveTo.Gallery → Pictures/<App name>
    onCancel = { /* user closed the camera */ },
    onError = { error -> /* no camera, capture failed, ... */ },
) { image ->
    // ...
}
```

`onPhoto` only runs when a photo was actually taken, so `image` is never null.

### The photo

| Property | |
|---|---|
| `image.uri` | `content://` Uri. Works with `setImageURI`, Coil, Glide, and sharing via Intents |
| `image.file` | The `File` (for `SaveTo.Cache`), or `null` for `SaveTo.Gallery` |
| `image.bitmap(maxSize = 2048)` | A decoded `Bitmap`, rotated upright and downscaled. Reads from disk, so call it off the main thread for big sizes |

### Coroutines

```kotlin
val image = camera.capture() ?: return   // null if the user cancelled
```

### Compose

```kotlin
var photo by remember { mutableStateOf<Photo?>(null) }

Button(onClick = { camera.open { photo = it } }) { Text("Take photo") }
```

## What you get for free

- Camera permission request, a re-ask button after denial, and an **Open settings** button once the user picks "don't ask again"
- Full-screen preview with tap-to-focus and pinch-to-zoom
- Flash (off / auto / on), shown only when the camera has one
- Front/back switch, shown only when the device has both
- Photos saved upright with the right rotation, and front-camera photos mirrored to match the preview
- Results delivered once your screen is resumed, so it's safe to navigate or commit fragments in the callback

## Good to know

- If Android kills your app's process while the camera is open (rare, and mostly on low-memory devices), the callback is lost along with the rest of the process.
- The callback runs on the screen instance that called `open`. If that screen is recreated while the camera is open (for example, your screen isn't orientation-locked and the user rotates the phone), keep the result in a `ViewModel`, or save `image.uri` with `rememberSaveable`, like any other state.

## Project layout

```
faciliscam/   the library
sample/       demo app (Compose)
```

Build and test:

```bash
./gradlew :faciliscam:testDebugUnitTest :sample:assembleDebug
```
