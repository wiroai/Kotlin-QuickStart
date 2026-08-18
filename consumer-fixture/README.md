# Clean consumer fixture

This Android library module depends on the **published** Maven artifact
`ai.wiro:wirokit:0.1.0` from `mavenLocal()` — not on `project(":wirokit")`.

## Run

```shell
./gradlew :wirokit:publishToMavenLocal
./gradlew :consumer-fixture:testDebugUnitTest
```

The tests construct a client, exercise Flow collection, handle typed errors,
and verify cooperative cancellation. They do not require live Wiro
credentials.
