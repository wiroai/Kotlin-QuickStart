# Contributing to WiroKit

Thank you for improving WiroKit.

## Development setup

Requirements:

- JDK 17;
- Android SDK 37;
- an Android emulator for instrumentation tests.

Run the local verification suite before opening a pull request:

```shell
./gradlew spotlessCheck detekt
./gradlew :wirokit:testDebugUnitTest :wirokit:verifyCoverageGate
./gradlew :app:testDebugUnitTest
./gradlew :wirokit:lintRelease :app:lintRelease
./gradlew :wirokit:assembleRelease :app:assembleRelease
./gradlew :wirokit:dokkaGenerate :wirokit:releaseApiCheck
./gradlew :wirokit:publishToMavenLocal
./gradlew :consumer-fixture:testDebugUnitTest
```

## Pull requests

- Keep changes focused and include tests for observable behavior.
- Preserve coroutine cancellation and never retry billable run or upload
  requests.
- Do not commit credentials, local SDK paths, generated reports, or signing
  material.
- Update the changelog for user-visible changes.
- Update the ABI dump with `:wirokit:releaseApiDump` only after reviewing
  the public API change.

## Releases

Releases are maintainer-only. Configure these GitHub Actions secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `SIGNING_IN_MEMORY_KEY`
- `SIGNING_IN_MEMORY_KEY_PASSWORD`

Set the same release version in `gradle/libs.versions.toml` and
`WiroKitInfo.kt`, then push a `vX.Y.Z` tag. The release workflow verifies the
tag, runs all release checks, signs and publishes the artifacts to Maven
Central, and creates the matching GitHub release. The publish job runs only
when the repository visibility is public.

## Live tests

Live tests can create billable Wiro tasks. Do not run them without explicit
authorization and short-lived credentials. Offline unit and instrumentation
tests must remain credential-free.

The scheduled API contract workflow only runs the read-only model search,
explore, and schema checks. It fails when `WIRO_API_KEY` is unavailable.
Configure `WIRO_API_KEY` and, when required, `WIRO_API_SECRET` as GitHub
Actions secrets.

## Code of conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
