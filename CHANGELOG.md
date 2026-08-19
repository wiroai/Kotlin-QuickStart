# Changelog

All notable changes to the Android WiroKit SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Android-only Kotlin SDK module `ai.wiro:wirokit` (minSdk 26).
- `WiroClient` with API-key, HMAC signature, and proxy authentication.
- Discovery APIs: `searchModels`, `explore`, `getModelSchema`.
- Run / task APIs: `runModel`, `getTask`, `getTaskById`, `cancelTask`,
  `killTask`.
- Uploads: byte arrays and streaming `ContentUri` inputs.
- Coroutine tracking: `watchTask`, `waitForTask`, `subscribe`,
  `subscribeStream` (polling and WebSocket). Both modes end on an equivalent
  terminal `/Task/Detail` snapshot.
- Typed request factories via `object Wiro` (13 models).
- Configurable `WiroClientLimits`, R8 consumer rules, and Kover ≥90%
  SDK-logic coverage gate.
- Jetpack Compose example app with Keystore-backed credentials.
- Dokka HTML documentation, Maven publishing metadata, CI workflow, and
  a clean `consumer-fixture` that resolves the local Maven publication.
