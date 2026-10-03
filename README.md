# Free TV Remote

Free, open-source Wi-Fi remote for **Android TV & Google TV**. No ads, no account, no cloud.

Turn your phone into the same kind of remote the Google TV app provides, using the Android TV Remote
protocol v2 over your local network. Written in Kotlin and Jetpack Compose, licensed under the
[GNU GPL v3](LICENSE).

> **Status: MVP, verified against a simulated TV only.** All protocol logic is covered by automated
> tests against an in-process fake TV, but it has **not yet been run on real hardware**. Please read
> [TESTING.md](TESTING.md) and help us fill in the compatibility table below.

<!-- Screenshots: add phone screenshots to fastlane/metadata/android/en-US/images/phoneScreenshots/ and reference them here. -->
*Screenshots: coming soon.*

## Features

- **Discovery** of TVs on your Wi-Fi (mDNS `_androidtvremote2._tcp`) or by typing an IP address
- **Pairing** with the 6-character code shown on the TV; the TV's certificate is pinned afterwards
- **Remote**: circular D-pad with OK, haptics and hold-to-repeat; Back, Home, Menu; volume and mute;
  power; play/pause, rewind, fast forward
- **Phone volume buttons** control the TV volume (can be switched off)
- **Keyboard**: type on the phone, send the text to the TV
- **App shortcuts**: Netflix, YouTube, Disney+, Prime Video and more via deep links; add your own
- **Several TVs**: save as many as you like, switch with one tap, reconnects to the last used one
- **Quick Settings tile** and a **home-screen widget** for power and volume
- **Settings**: haptics, keep screen on, theme (system / dark / light)
- **English and Hebrew** (full right-to-left support; the D-pad stays physically correct)
- Auto-reconnect with backoff; survives TV standby, Wi-Fi loss and screen rotation

## Install

- **GitHub Releases**: download the APK from the [Releases](../../releases) page.
- **F-Droid**: coming soon (metadata is already in `fastlane/metadata/android/`).

Requires Android 8.0 (API 26) or newer. On Android 17 and newer the app asks for the *local network*
permission, which Android requires before any app may talk to devices on your home network.

## How pairing works (3 steps)

1. Make sure the phone and the TV are on the **same Wi-Fi network** and the TV is on. Open the app and
   pick your TV from the list (or enter its IP address).
2. The TV shows a **6-character code**. Type it into the app.
3. Done. The app remembers the TV; next time it connects by itself.

## Compatibility

| Device | Status |
| --- | --- |
| NVIDIA Shield TV | **Needs testing** |
| TCL Google TV | **Needs testing** |
| Other Android TV / Google TV devices (Chromecast with Google TV, Sony, Philips, Xiaomi ...) | Expected to work, not tested |

Tested a device? Please file a *Device compatibility report* issue.

## Build

Requirements: JDK 17 or newer, Android SDK platform 37 and build-tools 36 (Android Studio installs them).

```sh
./gradlew assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew check                # unit + UI tests (Robolectric), lint, ktlint, detekt
./gradlew :protocol:test       # protocol module only (pure JVM)
```

Release signing is optional. Provide `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) in the project root or the environment variables `SIGNING_STORE_FILE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, then run
`./gradlew assembleRelease`. Without them the release APK is produced unsigned.

### Project layout

| Module | Contents |
| --- | --- |
| `protocol/` | Pure Kotlin/JVM: protobuf schema (Wire), message framing, pairing secret, TLS identity and pinning, pairing client, remote session, and the `FakeTv` test server |
| `app/` | Android app: data stores, discovery, session controller, Compose UI, tile and widget |

More background in [DECISIONS.md](DECISIONS.md).

## Privacy

Free TV Remote talks **only to the TV on your local network**. It has no analytics, no ads, no
trackers, no Google Play Services and no account. Nothing is sent to any server, and the app collects
no data. The pairing certificate and your saved TVs stay on your phone (the private key is encrypted
with a key from the Android Keystore) and are excluded from backups.

Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, and (Android 17+)
`ACCESS_LOCAL_NETWORK`.

## Credits

The Android TV Remote v2 protocol is not officially documented. The wire structure used here was
written from the knowledge shared by the community, with these projects consulted **for reference
only** (no code was copied; the implementation and `.proto` files are original):

- [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2) (Apache-2.0)
- [dgmltn/Dpad](https://github.com/dgmltn/Dpad)

Built with Kotlin, Jetpack Compose, [Wire](https://github.com/square/wire) (Apache-2.0) and
kotlinx.coroutines / serialization. "Android", "Google TV", "Netflix" and other names belong to
their owners; they are used only to describe compatibility and no logos are included.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
