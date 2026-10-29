<div align="center">

<img src="https://raw.githubusercontent.com/wiroai/Kotlin-QuickStart/main/.github/assets/wiro-logo.png" alt="Wiro AI" width="240">

# WiroKit for Android: Kotlin SDK for the Wiro AI API

Run image, video, audio and LLM models from Android apps in Kotlin with the Wiro AI API: typed requests, live task tracking and file uploads.

[![Kotlin 2.4+](https://img.shields.io/badge/Kotlin-2.4%2B-111111?style=for-the-badge&color=111111&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Android API 26+](https://img.shields.io/badge/Android-API%2026%2B-111111?style=for-the-badge&color=111111&logo=android&logoColor=white)](https://developer.android.com) [![CI](https://img.shields.io/github/actions/workflow/status/wiroai/Kotlin-QuickStart/ci.yml?branch=main&style=for-the-badge&color=111111&label=CI&logo=githubactions&logoColor=white)](https://github.com/wiroai/Kotlin-QuickStart/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-111111?style=for-the-badge&color=111111)](LICENSE)

[Website](https://wiro.ai) · [Models](https://wiro.ai/models) · [Docs](https://wiro.ai/docs/code-examples) · [Dashboard](https://wiro.ai/panel)

</div>

WiroKit for Android is the official Kotlin library for calling the
[Wiro AI](https://wiro.ai) model API from Android apps (API 26+). It runs any
model in the Wiro AI catalog, through typed request factories for popular
image, video and music models or through `Wiro.model` with a parameter map,
and follows each task by polling or over a WebSocket with coroutines and
Flows. The repo also contains a Jetpack Compose example app that generates
images with FLUX.2 Pro.

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
- A [Wiro AI project and API key](https://wiro.ai/panel/project/new)

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

> **Mobile tip:** Prefer `WiroClient(proxyUrl = ..., headers = ...)` in shipped
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

Your backend attaches Wiro AI credentials server-side. The SDK never stores an
API key in proxy mode.

## Which call do I need?

| I want to... | Call |
| --- | --- |
| Generate with a supported model | `client.subscribe(Wiro.flux2Pro(...))` |
| Run any other model | `client.subscribe(Wiro.model("owner/project", ...))` |
| Fire-and-forget then wait | `run` / `runModel` then `waitForTask` |
| Stream live status updates | `subscribeStream(...)` |
| Find a model | `searchModels` / `explore` |
| Inspect parameters | `getModelSchema` then `schema.validate` |
| Send bytes | `WiroFileInput.Bytes(...)` |
| Send a content URI | `WiroFileInput.ContentUri(...)` + content source |
| Stop work | Cancel the coroutine, or `cancelTask` / `killTask` |

## Typed and dynamic requests

Typed factories are available for these models. Any other model runs through
`Wiro.model`.

| Category | Models |
| --- | --- |
| Image | FLUX.2 Pro, GPT Image 2, Nano Banana Pro, Seedream v4, Grok Imagine Image |
| Video | Runway Gen-4.5, Seedance 2.0, Kling V3, Veo 3.1, Sora 2 Pro, Hailuo 2.3 Fast, Grok Imagine Video |
| Music | Lyria 3 |

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

- Use proxy mode in release builds. The example app's minified release build
  accepts only a backend proxy URL.
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
- Available models: [https://wiro.ai/models](https://wiro.ai/models)
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

## More from Wiro AI

| Repository | What it is |
| --- | --- |
| [Wiro-MCP](https://github.com/wiroai/Wiro-MCP) | Official MCP server for Wiro AI, [`@wiro-ai/wiro-mcp` on npm](https://www.npmjs.com/package/@wiro-ai/wiro-mcp) |
| [Wiro-Plugin](https://github.com/wiroai/Wiro-Plugin) | Wiro AI plugin for Cursor, Claude Code, Gemini CLI and Devin |
| [Swift-QuickStart](https://github.com/wiroai/Swift-QuickStart) | WiroKit, the Wiro AI SDK for iOS |
| [Dart-QuickStart](https://github.com/wiroai/Dart-QuickStart) | Wiro AI SDK for Dart and Flutter, [`wiro` on pub.dev](https://pub.dev/packages/wiro) |
| [ReactNative-QuickStart](https://github.com/wiroai/ReactNative-QuickStart) | Wiro AI SDK for React Native and Expo, [`@wiro-ai/wiro-react-native` on npm](https://www.npmjs.com/package/@wiro-ai/wiro-react-native) |
| [Swift-Realtime](https://github.com/wiroai/Swift-Realtime) | Realtime voice AI example for iOS |
| [Kotlin-Realtime](https://github.com/wiroai/Kotlin-Realtime) | Realtime voice AI example for Android |
| [Dart-Realtime](https://github.com/wiroai/Dart-Realtime) | Realtime voice AI example for Flutter |
| [ReactNative-Realtime](https://github.com/wiroai/ReactNative-Realtime) | Realtime voice AI example for React Native |
| [n8n-nodes-wiroai](https://github.com/wiroai/n8n-nodes-wiroai) | Wiro AI nodes for n8n, [`@wiro-ai/n8n-nodes-wiroai` on npm](https://www.npmjs.com/package/@wiro-ai/n8n-nodes-wiroai) |
| [Wiro-Docs](https://github.com/wiroai/Wiro-Docs) | Source of the Wiro AI API docs |

[Website](https://wiro.ai) · [Models](https://wiro.ai/models) · [Docs](https://wiro.ai/docs) · [X](https://x.com/wiroai)

## License

MIT. See [LICENSE](LICENSE).

<div align="center">

<img src="https://wiro.ai/images/koala/accent-heavy-koala.png" alt="Wiro AI koala mascot" width="80">

</div>
