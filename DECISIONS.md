# Decision log

Short records of the architectural and process choices behind the project, newest sections last.
Each entry: what was decided, why, and what it costs.

## Process

**D1. License is GPL-3.0-only.** The repository was created with an AGPL-3.0 `LICENSE`; it was replaced
with the official GPL-3.0 text because the project goal says GPL-3.0. The app has no server side, so the
AGPL network clause would add nothing.

**D2. Work was committed on `claude/project-review-z5a8a8`, not pushed.** The session's GitHub credentials
had no write access (`git push` and the GitHub API both answered 403), so nothing could be pushed and
**CI has never run on GitHub**. Everything was built and tested locally with the same Gradle commands CI uses.
Branch strategy once access exists: merge to `main` after a green CI run.

## Toolchain

**D3. Latest stable toolchain.** AGP 9.4.1, Gradle 9.8, Kotlin 2.4.20, Compose BOM 2026.09, `compileSdk`
and `targetSdk` 37 (Android 17), `minSdk` 26. AGP 9 has built-in Kotlin, so the app module does not apply
`kotlin-android`. Cost: newer tooling means fewer Stack Overflow answers; benefit: no migration debt.

**D4. Android 17 local network permission.** SDK 37 introduces the runtime permission
`android.permission.ACCESS_LOCAL_NETWORK`, which apps targeting 37 need for LAN sockets and mDNS. It is declared
in the manifest and requested on the onboarding screen (with a rationale and an "open settings" fallback) and on
the remote screen if it is missing. It is only checked on API 37+. The tile and widget cannot ask; they report
failure and the user opens the app.

**D5. JDK.** Builds on JDK 17+ (CI uses 21). Bytecode target is 17 for both modules; no Gradle toolchain
auto-provisioning (so the build also works offline and on F-Droid's build server).

## Protocol module

**D6. Pure Kotlin/JVM `protocol` module** with explicit-API mode, no Android dependency, so everything is
unit-testable on the JVM and a fake TV can run in tests.

**D7. Wire for protobuf, `.proto` files written from scratch** (proto3). Wire has no reflection and generates
plain Kotlin. Unknown fields from newer TVs are ignored (covered by a test). Key codes are plain `int32` instead
of a proto enum so unknown keys never break decoding.

**D8. No BouncyCastle: a 70-line DER writer builds the self-signed X.509 certificate.** Keeps the app small and
the dependency list short; the output is parsed by `CertificateFactory` and verified in tests.

**D9. TLS trust model.** TVs use self-signed certificates, so normal PKI validation is impossible. During pairing
the certificate is accepted (trust on first use, authenticated by the code on the TV screen) and then **pinned as
the SHA-256 of its SubjectPublicKeyInfo**. A mismatch ends in `CERTIFICATE_MISMATCH` (user must re-pair).

**D10. Identity storage: software RSA key wrapped by a Keystore AES-GCM key** instead of an RSA key held inside
the Keystore. Reason: Keystore RSA keys need extra digest/padding authorisation to work with TLS 1.2 PKCS#1 *and*
TLS 1.3 PSS client authentication, and a failure would only show on real TVs. A software key is signed by the
normal JCA provider and is certain to be compatible; the file is excluded from backups and encrypted at rest.
If the Keystore is unusable a marker byte records plain storage (still private app storage). If the Keystore key
is lost the identity is regenerated and TVs must be paired again.

**D11. Pairing secret** is `SHA-256(clientModulus | clientExponent | serverModulus | serverExponent | codeTail)`
with minimal unsigned big-endian numbers; the first digest byte must equal the first two code characters, which
catches typos locally without bothering the TV. Verified against an independent reference implementation in tests.

**D12. Remote session behaviour.**
- Pings are answered before anything else.
- No data for 20 s (the TV pings every few seconds) means the TV vanished; the socket is dropped and retried.
- Backoff 1, 2, 4, 8, 15, 30 s (cap), reset after a completed handshake.
- A connection that is closed before the handshake completes twice in a row is reported as `NOT_PAIRED`
  (a real TV does this for unknown clients) instead of retrying forever.
- Writes go through a lock; commands return `false` instead of throwing when disconnected.
- Blocking socket I/O runs on `Dispatchers.IO`; cancellation closes the socket to unblock reads.

**D13. Text input** uses the IME batch-edit message with counters learned from the TV's IME events.
This part of the protocol is the least documented, so it is marked "needs real-device verification".

## App

**D14. The session lives in `RemoteController`, an application-scoped object, not in a Composable or a
ViewModel, and not in a foreground service.** Rotation and navigation cannot affect it. It follows the process
lifecycle: connected while the app is visible, kept for 30 s after leaving, then disconnected to save battery.
A foreground service would keep a notification on screen and, on Android 14+, requires a declared type for a
use case that does not need it. The tile and widget use short-lived connections (`sendQuickKey`).

**D15. Key gestures.** Two behaviours, implemented in a plain coroutine class (`KeyGestures`) so they are tested
with virtual time: `REPEAT` (D-pad arrows, volume, rewind/forward: one key on press, repeated taps while held,
which is deterministic across TVs) and `TAP_OR_LONG` (OK, Back, Home, Menu, Power, play/pause: tap on release, or a
real long press via `START_LONG`/`END_LONG` after 500 ms). `releaseAll()` runs on pause so a lost touch never
leaves a key held down on the TV.

**D16. D-pad built from four clipped ring sectors plus a centre button** instead of one custom-drawn canvas.
Each sector is its own accessibility node (TalkBack can activate it) and hit-testing follows the clip shape.
Verified with Robolectric touch tests and screenshots.

**D17. Right-to-left.** The D-pad is forced left-to-right internally: "left" must always be the physical left
because it mirrors the TV's spatial navigation. Volume and transport rows are also forced LTR (− left, + right,
rewind left). Everything else mirrors. A Unicode LRM keeps brand names like "Disney+" intact in Hebrew text.

**D18. Hebrew resources live in `values-iw`** (lint `LocaleFolder`: Android uses the legacy code, `values-he` is
ignored). `localeFilters` therefore must include `iw`; with only `he` the Hebrew strings were silently stripped from
the APK (found by inspecting the APK, now guarded by a unit test and a CI step). `locales_config.xml` uses `he`.
For Weblate set the file mask for `he` to `values-iw/strings.xml`. Fastlane metadata uses `he` as F-Droid expects.

**D19. Default theme is Dark** (a remote is mostly used in a dark room); System and Light are one tap away.

**D20. Tile and widget use plain `TileService` and `RemoteViews`**, not Jetpack Glance: smaller, no extra
dependency, enough for four buttons.

**D21. Shortcut links are validated as `scheme://...`.** Built-in shortcuts can be disabled but not deleted; custom
ones can be reordered and deleted. Brand names are plain text, no logos are shipped.

**D22. Storage** is DataStore Preferences with JSON for lists (kotlinx.serialization). No database: the data is a
handful of small records.

**D23. Backups are disabled** (`allowBackup=false` plus explicit extraction rules): pairings are bound to the device
identity and would be useless after a restore.

## Testing

**D24. Three layers.** (1) Pure JVM unit tests for framing, protobuf bytes, secret, certificates and identity store.
(2) End-to-end tests over real TLS sockets against `FakeTv` (pairing, wrong code, rejection, keys, links, text, pings,
reconnect, silent TV, unreachable TV, wrong certificate, unpaired phone, multi-TV, lifecycle). (3) Robolectric Compose
tests: D-pad touch mapping in LTR/RTL, Hebrew accessibility labels, screenshots of every screen. The fake TV lives in
`protocol/src/testFixtures` so the app module's tests use the same implementation.

**D25. The fake TV is written by the same author as the client**, so it proves internal consistency and
robustness, not conformance with real TVs. That gap is exactly what TESTING.md covers.
