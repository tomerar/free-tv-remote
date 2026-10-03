# Good first issues (drafts)

Ready to paste into GitHub. Suggested labels are in brackets.

## 1. Add Spanish translation  [good first issue, i18n]
Copy `app/src/main/res/values/strings.xml` to `values-es/strings.xml` and translate it. Add `es` to
`app/src/main/res/xml/locales_config.xml` and to `localeFilters` in `app/build.gradle.kts`.
**Done when** `./gradlew check` is green (`I18nResourcesTest` compares keys and placeholders; extend it to
the new language) and the screenshots in `app/build/screenshots` look right.

## 2. Add more built-in app shortcuts  [good first issue]
Add presets (e.g. Max, Hulu, YouTube Music, Crunchyroll, Twitch) to `AppShortcut.defaults`, disabled by default,
with their deep links. **Done when** the links are verified on a real TV and a test checks that ids are unique.

## 3. Show friendly app names for "Now showing"  [done in v0.1.7]
The status card now shows the readable name the TV sends (`label`), falls back to a small map of known packages
(`friendlyAppName`) and, in the opened details only, to the package id. Logic and tests: `AppName.kt`,
`StatusFormatTest`. Remaining idea: extend the map with more apps once real TVs report their labels.

## 4. Extract and test the mDNS result mapping  [good first issue, testing]
`NsdTvDiscovery` mixes Android callbacks and mapping of `NsdServiceInfo` to `DiscoveredTv`. Extract the pure parts
(IPv4 preference, name handling, sorting) so they can be unit tested without an emulator.

## 5. Screenshot regression tests  [testing]
`ScreensScreenshotTest` writes PNGs but does not compare them. Add golden-image comparison (for example with
Roborazzi) for the remote screen in LTR and RTL.

## 6. Wake-on-LAN for TVs that are fully off  [enhancement]
When the TV is unreachable, offer to send a Wake-on-LAN magic packet (needs the TV's MAC address, which can be
stored at pairing time). Make it optional and explain that many TVs do not support it.

## 7. Configurable long-press and repeat timing  [good first issue, enhancement]
`GestureTiming` already holds the delays. Add two sliders in Settings (repeat speed, long-press delay) backed by
`SettingsRepository`, and pass them to `KeyGestures`.

## 8. Touchpad mode  [enhancement]
An optional swipe area that maps swipes to D-pad presses and a tap to OK, as a second layout next to the D-pad.

## 9. Accessibility audit with TalkBack and Switch Access  [accessibility]
Walk through every screen with TalkBack, Switch Access and large fonts; file or fix issues (focus order, labels,
state descriptions for the connection card).

## 10. Add Weblate configuration  [i18n, infrastructure]
Add a `weblate` component file mask for `app/src/main/res/values-*/strings.xml` (remember that Hebrew is `values-iw`)
and document it in CONTRIBUTING.md.

## 11. Re-find a saved TV after its IP address changes  [enhancement, reliability]
See "A saved TV is not found again after its IP address changes" in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for
the pinned-key probe design. **Done when** a saved TV whose address changed reconnects without re-pairing and without
creating a duplicate, and a different device at the old address is never accepted (tests against `FakeTv`).

## 12. Show when keys cannot be delivered  [enhancement, UX]
Dim or disable the remote controls while the connection is down, instead of silently dropping presses.

## 13. Reuse the temporary quick-key connection for a few seconds  [enhancement]
Rapid widget taps (volume up several times) each open a new connection today. Keep one temporary session alive
for a short idle window.

## Research: LG webOS and Samsung Tizen remotes  [research, help wanted]
Neither platform implements the Android TV Remote protocol, so supporting them means a separate client per
platform. First step is research only: how pairing works, what is publicly documented, and whether a design fits the
project's rules (open source, local only, no cloud, no tracking). **Done when** a short design note per platform
exists in `docs/`, with the open questions. Owners of such TVs who can test a prototype are especially welcome.
See [COMPATIBILITY.md](COMPATIBILITY.md#lg-and-samsung).
