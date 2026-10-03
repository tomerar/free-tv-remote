# Building

Requirements: **JDK 17 or newer**, Android SDK **platform 37** and **build-tools 36** (Android Studio installs
them).

```sh
git clone https://github.com/tomerar/free-tv-remote
cd free-tv-remote

./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew check             # unit + UI tests, Android lint, ktlint, detekt
./gradlew :protocol:test    # the pure-JVM protocol module only
./gradlew assembleRelease   # release APK (signed only if a key is configured, see below)
```

## Signing

Release signing is optional locally. Put `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in a
`keystore.properties` file (git-ignored), or set `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Without them the release APK is built unsigned; a partial
configuration fails. Publishing a release is described in [RELEASING.md](RELEASING.md).

## Repository layout

| Path | What is in it |
| --- | --- |
| [`protocol/`](../protocol) | Pure Kotlin/JVM library with no Android dependency: protobuf schema (Wire), message framing, pairing secret, TLS identity and pinning, pairing client, remote session, and a `FakeTv` server used by tests |
| [`app/`](../app) | The Android app: data stores, discovery, session controller, Jetpack Compose UI, tile, widget, diagnostics |
| [`fastlane/`](../fastlane) | Store metadata (English, Hebrew) for F-Droid |
| [`docs/`](.) | Documentation |
| [`TESTING.md`](../TESTING.md) | Manual test checklist for real devices |
| [`DECISIONS.md`](../DECISIONS.md) | Why things are built the way they are |

## Quality gates

`./gradlew check` runs, in CI on every push and pull request: unit and Robolectric UI tests, Android lint (warnings
are errors), ktlint and detekt. Release builds run the same checks before publishing.
