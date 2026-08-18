# WiroKit for Android

[![CI](https://github.com/wiroai/Kotlin-QuickStart/actions/workflows/ci.yml/badge.svg)](https://github.com/wiroai/Kotlin-QuickStart/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org)
[![Android API 26+](https://img.shields.io/badge/Android-API%2026%2B-green.svg)](https://developer.android.com)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Official Android Kotlin SDK for discovering and running AI models on
[Wiro](https://wiro.ai).

## Features

- Typed request factories for popular image, video, and audio models
- Dynamic model requests with `Wiro.model("owner/project", parameters)`
- Model search, explore, and schema validation
- `subscribe` / `run` / `subscribeStream` task lifecycle APIs
- Automatic file uploads for bytes and `ContentUri` inputs
- Polling and WebSocket task tracking
- Task cancel / kill
- Retry with exponential backoff, timeouts, and structured logging
- API key, HMAC signature, and proxy authentication
- Coroutine-native cancellation and lifecycle-friendly Flows

## Requirements

- Android `minSdk` 26+
- Kotlin 2.4+
- JDK 17+ for builds
- A [Wiro project and API key](https://wiro.ai/panel/project/new)

## Installation

The first public artifact has not been published to Maven Central yet. To
validate a local build:

```shell
./gradlew :wirokit:publishToMavenLocal
```

Add `mavenLocal()` before `mavenCentral()` in the consuming project, then use
the release candidate coordinates:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

After the first Maven Central release, remove `mavenLocal()` and use one of the
following dependency declarations.

### Gradle (version catalog)

```kotlin
// libs.versions.toml
[libraries]
wirokit = { module = "ai.wiro:wirokit", version = "0.1.0" }

// module build.gradle.kts
dependencies {
    implementation(libs.wirokit)
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.wiro:wirokit:0.1.0")
}
```

## Quick start

```kotlin
import ai.wiro.wirokit.Wiro
import ai.wiro.wirokit.WiroClient
import ai.wiro.wirokit.WiroTaskResult
import ai.wiro.wirokit.subscribe

public suspend fun generateImage() {
    val client = WiroClient(apiKey = "your-api-key")
    try {
        val result = client.subscribe(
            Wiro.flux2Pro(
                prompt = "A cinematic mountain lake",
                width = 1024,
                height = 1024,
            ),
        )

        when (result) {
            is WiroTaskResult.Success ->
                println(result.task.outputs.firstOrNull()?.url)
            is WiroTaskResult.Failure ->
                println("Failed: ${result.reason}")
        }
    } finally {
        client.close()
    }
}
```

> **Mobile tip:** Prefer `WiroClient(proxyUrl = …, headers = …)` in shipped
> apps so long-lived API secrets never ship inside the binary.

## Authentication

### API key

```kotlin
val client = WiroClient(apiKey = "your-api-key")
```

### API key + HMAC signature

```kotlin
val client = WiroClient(
    apiKey = "your-api-key",
    apiSecret = "your-api-secret",
)
```

### Proxy (recommended for production)

```kotlin
val client = WiroClient(
    proxyUrl = "https://api.myapp.com/wiro/v1",
    headers = mapOf("Authorization" to "Bearer app-token"),
)
```

Your backend attaches Wiro credentials server-side. The SDK never stores an
API key in proxy mode.

## Which call do I need?

| I want to… | Call |
| --- | --- |
| Generate with a supported model | `client.subscribe(Wiro.flux2Pro(...))` |
| Run any other model | `client.subscribe(Wiro.model("owner/project", …))` |
| Fire-and-forget then wait | `run` / `runModel` then `waitForTask` |
| Stream live status updates | `subscribeStream(...)` |
| Find a model | `searchModels` / `explore` |
| Inspect parameters | `getModelSchema` then `schema.validate` |
| Send bytes | `WiroFileInput.Bytes(...)` |
| Send a content URI | `WiroFileInput.ContentUri(...)` + content source |
| Stop work | Cancel the coroutine, or `cancelTask` / `killTask` |

## Typed and dynamic requests

```kotlin
// Typed
val request = Wiro.flux2Pro(
    prompt = "Sunset over the bay",
    width = 1024,
    height = 1024,
)

// Dynamic model request
val dynamic = Wiro.model(
    slug = "black-forest-labs/flux-2-pro",
    parameters = mapOf(
        "prompt" to WiroValue.StringValue("Sunset over the bay"),
        "width" to WiroValue.number(1024),
        "height" to WiroValue.number(1024),
    ),
)
```

`google/upscaler` is not part of the supported typed API.

## Polling and WebSocket tracking

```kotlin
import ai.wiro.wirokit.WiroTaskTrackingMode
import ai.wiro.wirokit.subscribeStream
import kotlinx.coroutines.flow.collect

// Default: polling `/Task/Detail`
client.subscribeStream(request).collect { update ->
    println(update.status?.apiValue)
}

// WebSocket with detail + polling fallback on early close
client.subscribeStream(
    request = request,
    trackingMode = WiroTaskTrackingMode.WEB_SOCKET,
).collect { update ->
    println(update.status?.apiValue)
}
```

`subscribeStream` is `suspend` and completes the billable `/Run` **before**
returning the `Flow`, so collecting (or re-collecting) never repeats the run.

## Uploads

```kotlin
import ai.wiro.wirokit.WiroFileInput
import ai.wiro.wirokit.WiroUriContentSource

// In-memory bytes
val bytes = WiroFileInput.Bytes(
    bytes = imageBytes,
    fileName = "photo.png",
)

// Content URI (streamed; not fully buffered by default)
val uriInput = WiroFileInput.ContentUri(
    uri = contentUri,
    fileName = "photo.png",
)
val source = WiroUriContentSource.from(contentResolver)
client.uploadFile(uriInput, source)
// or ContentResolver overload: client.uploadFile(uriInput, contentResolver)
```

Unresolved file inputs inside run parameters are uploaded automatically before
`/Run`.

## Coroutine cancellation and lifecycle

- Cancel the coroutine / `Job` that is collecting a Flow to stop local work
  immediately (`CancellationException` is preserved).
- Call `cancelTask(taskId)` when a queued task id is known.
- Call `killTask(token)` or `killTask(id)` to stop a remote worker.
- Tie collection to `viewModelScope` or `lifecycleScope` so configuration
  changes do not leak work. Do **not** auto-restart a billable run after
  process death unless the user explicitly taps Generate again.
- Always `close()` the client when you own it (or use a single app-scoped
  instance).

## Security guidance

- Minified release builds enforce proxy mode.
- Never log API keys, secrets, proxy bearer tokens, or raw response bodies.
- SDK exceptions redact sensitive values from `toString()` / messages where
  applicable; use `rawResponseBody` only for local diagnostics.
- The example app encrypts local development credentials with Android
  Keystore-backed AES-GCM. Do not commit credentials.
- Keep `allowBackup=false` for apps that store secrets locally.

## Example app

Open the `app` run configuration in Android Studio, or:

```shell
./gradlew :app:assembleDebug
```

Configure an API key or proxy URL in **Settings** for a debug build. Minified
release builds only accept a backend proxy URL. The demo generates a Flux 2
Pro image with live status, Coil rendering, and local/API cancel/kill.

## Documentation

- Product docs: [https://wiro.ai/docs](https://wiro.ai/docs)
- Generated API docs (Dokka): `./gradlew :wirokit:dokkaGenerate`
  → `wirokit/build/dokka/html/index.html`
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- Security policy: [`SECURITY.md`](SECURITY.md)
- Contributing guide: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- Local consumer fixture: [`consumer-fixture/`](consumer-fixture/)

## Development

```shell
./gradlew :wirokit:testDebugUnitTest :wirokit:verifyCoverageGate
./gradlew :app:testDebugUnitTest
./gradlew :wirokit:assembleRelease
./gradlew spotlessCheck detekt
./gradlew :wirokit:lintRelease :app:lintRelease
./gradlew :wirokit:releaseApiCheck
./gradlew :wirokit:publishToMavenLocal
./gradlew :consumer-fixture:testDebugUnitTest
```

## License

MIT — see [LICENSE](LICENSE).
