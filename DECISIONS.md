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

**D19. Default theme follows the system** (it was Dark until v0.1.3: a remote is often used in a dark room, but the
app should behave like other apps by default); Dark and Light are one tap away. See D24 for colors.

**D20. Tile and widget use plain `TileService` and `RemoteViews`**, not Jetpack Glance: smaller, no extra
dependency, enough for four buttons.

**D21. Shortcut links are validated as `scheme://...`.** Built-in shortcuts can be disabled but not deleted; custom
ones can be reordered and deleted. Brand names are plain text, no logos are shipped.

**D22. Storage** is DataStore Preferences with JSON for lists (kotlinx.serialization). No database: the data is a
handful of small records.

**D23. Backups are disabled** (`allowBackup=false` plus explicit extraction rules): pairings are bound to the device
identity and would be useless after a restore.

**D24. Colors follow the phone (Material You) on Android 12+, with a Settings switch to turn it off.** The teal
scheme remains the fallback for older phones and when the switch is off. Window width is capped (640 dp, centered)
so tablets and unfolded screens do not stretch the remote. Every tappable control of the remote is checked to be at
least 48 x 48 dp (`TouchTargetTest`).

**D25. Searching for TVs is explicit and bounded.** Nothing listens on the network until the user taps "Search for
TVs"; the search ends by itself after 15 s with a progress bar, and leaving the screen cancels it. When nothing is
found the screen explains what to check and opens the manual IP entry. Rationale: continuous mDNS discovery drains
the battery and gave no signal about whether anything was happening (`DiscoverViewModel`, `DiscoverViewModelTest`).

**D26. Closing a connected TLS socket never happens on the main thread.** It writes a close alert, and Android
throws `NetworkOnMainThreadException` for that on the main thread; after pairing succeeded this could close the app
(the pairing was already saved, so reopening worked). `closeOffThread` closes on a short-lived daemon thread
(`SocketIo.kt`). Background coroutine failures in the application scope are recorded instead of crashing the app.
The first fix was made from reading the code and the symptoms; confirm on a device (TESTING.md, B2).

**D27. A local, rolling event log with copy/share (Settings > Diagnostics).** Two files of at most 5 MB (10 MB in
total) in app-private storage; "Copy log" puts the newest 100 KB on the clipboard (the system limits what the
clipboard accepts), "Share" sends the whole log as a file through a `FileProvider` limited to that file. It records
pairing steps, connection state changes, discovery results and errors/crashes, never pairing codes, keys, typed text
or key presses; the last octet of IPv4 addresses is masked. Nothing is sent anywhere unless the user shares it. It
replaced the earlier "last crash" file.

**D28. The status card has two states.** Closed (default, one line): connection, TV power, volume, app. Open (tap):
model/vendor, volume bar, app, address, connection time, a keyboard shortcut when the TV asked for text, and a link
to Diagnostics. Problems and their actions (permission, Reconnect, Pair again) are never behind the tap. The app name
comes from the TV's own `label` when sent, else a small package map, else (open state only) the package id. All of
it is derived in pure functions (`AppName.kt`) with unit tests; the TV's text-field request is treated as best
effort (see docs/KNOWN_LIMITATIONS.md).

**D29. Choosing a TV that is already paired selects it and opens the remote.** Matching is by the saved address.
Pairing again is offered only through the connection card (*Pair again*) when the TV reports the phone is no longer
paired. Rationale: re-pairing a known TV asked for a code the app did not need and looked like a bug
(`DiscoverViewModel.choose`, `DiscoverViewModelTest`). Known gap: a paired TV whose IP address changed is not
recognised and is paired as a new one (docs/KNOWN_LIMITATIONS.md).

**D30. The TV screen has two sections, like Android's Wi-Fi settings.** *Saved TVs* (always visible; tap selects the TV
and opens the remote) and *Available TVs* (only newly found ones, with the search). A saved TV that answers the search
shows *On this network* and is never offered for pairing again.

**D31. A saved TV is recognised by the name it announces on the network, and only moved after a pin check.** The mDNS
name is recorded when a TV is paired from the search (`SavedTv.serviceName`, optional, so old data still loads). When a
search finds that name at a different address, the app first completes a TLS handshake with the pinned-key check
(`confirmPinnedTv`; nothing is sent) and only then updates the address, so a look-alike device can never take over a
saved entry. Ambiguous names (two saved TVs, or one name announced twice) are ignored. Rationale: routers hand out new
addresses and the app used to lose the TV. Re-pairing a TV found at a new address replaces its entry instead of adding
a duplicate (`TvRepository.savePaired`).

**D32. The remote is laid out like a physical TV remote (v0.2.0).** A centered rounded body holds the keys in three
aligned columns: mute and power, input and menu, the D-pad, back / keyboard / home (the keyboard where a remote has its
microphone; there is no voice support), TV settings and guide, then VOL and CH rockers with play/pause and info between
them, and the app keys as a three-column grid at the bottom. Two earlier drafts were rejected by the maintainer: a
vertical volume rail beside the D-pad (unbalanced) and a single centered column (did not read as a remote). The model
was a TCL Google TV remote. Rewind and fast forward were dropped as separate keys (not on the reference remote; the
D-pad scrubs in most players). Brand logos are still not used (D21): app keys are colored letter circles. Tested as a
whole (`RemoteLayoutTest`: every key present, 48 dp everywhere including a 320 dp phone, Hebrew).
**Not done on purpose:** swiping on the D-pad or the rockers. A press sends its key immediately, so a swipe that starts
on a key would first fire that key; making presses wait would add latency to every tap. A separate touchpad mode is
listed in docs/ISSUES.md.

**D33. New launcher icon: the D-pad as a symbol** (four quarter rings and the OK dot, the right ring lit). Original
artwork as a vector drawable, with a separate monochrome layer for Android 13 themed icons.

**D34. Pairing restarts itself after two local code mismatches.** The code check runs on the phone against the
certificate recorded on that connection, so a mismatch means the code does not fit this connection (typo or a stale
session), never that the TV refused it. One miss keeps the session (typo); a second in a row reconnects and the
screen says the TV shows a new code. Failure logs also name the platform exception, because R8 renames ours.

**D35. Sleep timer on the phone, safe power-off.** There is no known command to start the TV's own sleep timer over
the remote protocol, and a vendor menu cannot be driven reliably, so the timer runs on the phone: persisted state
(DataStore), one `AlarmManager.setExactAndAllowWhileIdle` alarm (approximate fallback when exact alarms are not
allowed), a short foreground service for the connection, a countdown notification the system draws itself. A
reconcile at start, boot, update and permission change restores the alarm; a timer that ended while it could not run
is *missed* and never fires late. Power is a toggle, so the key goes out only if the TV reports "on" on the current
connection (`TvState.isOnFresh`), once, never retried after the write. The open remote's connection is reused;
otherwise a short extra one is opened. No dependency was added (no WorkManager). Not done: TV-side scheduling,
companion app, lock mode (see KNOWN_LIMITATIONS).

## Testing

**D24. Three layers.** (1) Pure JVM unit tests for framing, protobuf bytes, secret, certificates and identity store.
(2) End-to-end tests over real TLS sockets against `FakeTv` (pairing, wrong code, rejection, keys, links, text, pings,
reconnect, silent TV, unreachable TV, wrong certificate, unpaired phone, multi-TV, lifecycle). (3) Robolectric Compose
tests: D-pad touch mapping in LTR/RTL, Hebrew accessibility labels, screenshots of every screen. The fake TV lives in
`protocol/src/testFixtures` so the app module's tests use the same implementation.

**D25. The fake TV is written by the same author as the client**, so it proves internal consistency and
robustness, not conformance with real TVs. That gap is exactly what TESTING.md covers.
