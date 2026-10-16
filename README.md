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

Then add **one** of these dependencies:

```kotlin
// Jetpack Compose apps: includes PhotoBox, PhotoImage, rememberPhoto, and the core library
implementation("com.github.al3j4ndrolm.FacilisCam:faciliscam-compose:1.1.0")

// Views-only apps: just camera.open { }
implementation("com.github.al3j4ndrolm.FacilisCam:faciliscam:1.1.0")
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

`Photo` is `Parcelable`, so you can keep it in `rememberSaveable`, a `SavedStateHandle` or a `Bundle`.

### Coroutines

```kotlin
val image = camera.capture() ?: return   // null if the user cancelled
```

## Compose

With `faciliscam-compose`, pick whichever level fits the screen:

### 1. `PhotoBox`: the whole thing in one line

```kotlin
PhotoBox(Modifier.size(240.dp))
```

It shows a "Take photo" placeholder, opens the camera when tapped, then shows the photo. Tap it again to retake. To use the photo:

```kotlin
PhotoBox(Modifier.size(240.dp)) { photo -> upload(photo.uri) }
```

You can customize it with `lens`, `saveTo`, `shape` (e.g. `CircleShape` for avatars), `contentScale` and `placeholder`.

### 2. `rememberPhoto()`: your own button and layout

```kotlin
val photo = rememberPhoto()

Button(onClick = { photo.take() }) { Text("Take photo") }
PhotoImage(photo, Modifier.fillMaxWidth())
```

`photo.value` is the current `Photo?`. `photo.take(lens = Lens.Front)` opens the selfie camera, and `photo.clear()` resets it. The state survives rotation, even if the user rotates while the camera is open.

### 3. `PhotoImage`: just display it

```kotlin
var photo by rememberSaveable { mutableStateOf<Photo?>(null) }

Button(onClick = { camera.open { photo = it } }) { Text("Take photo") }
PhotoImage(photo, Modifier.fillMaxWidth())
```

`PhotoImage` decodes in the background, upright and sized for the screen, and draws nothing while the photo is `null`. `Photo` is `Parcelable`, so `rememberSaveable` works as shown.

## What you get for free

- Camera permission request, a re-ask button after denial, and an **Open settings** button once the user picks "don't ask again"
- Full-screen preview with tap-to-focus and pinch-to-zoom
- Flash (off / auto / on), shown only when the camera has one
- Front/back switch, shown only when the device has both
- Photos saved upright with the right rotation, and front-camera photos mirrored to match the preview
- Results delivered once your screen is resumed, so it's safe to navigate or commit fragments in the callback

## Good to know

- If Android kills your app's process while the camera is open (rare, and mostly on low-memory devices), the callback is lost along with the rest of the process.
- The callback runs on the screen instance that called `open`. If that screen is recreated while the camera is open (for example, your screen isn't orientation-locked and the user rotates the phone), keep the result in a `ViewModel` or `rememberSaveable`, like any other state. In Compose, `rememberPhoto()` and `PhotoBox` handle this for you.

## Project layout

```
faciliscam/           core library: camera.open { }
faciliscam-compose/   Compose components: PhotoBox, PhotoImage, rememberPhoto
sample/               demo app
```

Build and test:

```bash
./gradlew :faciliscam:testDebugUnitTest :faciliscam-compose:assembleRelease :sample:assembleDebug
```
