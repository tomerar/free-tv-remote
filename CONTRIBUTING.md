# Contributing

Thanks for helping! Bug reports, device compatibility reports, translations and code are all welcome.
Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Quick start

```sh
git clone https://github.com/tomerar/free-tv-remote
cd free-tv-remote
./gradlew check assembleDebug
```

You need JDK 17+ and the Android SDK (platform 37, build-tools 36).

## Ground rules

- **No proprietary or tracking dependencies.** No Google Play Services, analytics, ads or crash
  reporters. The app must stay buildable by F-Droid. New dependencies need a clear license and a reason.
- **Keep `protocol/` free of Android APIs.** It is pure Kotlin/JVM so it can be tested without a device.
- **Test what you change.** Protocol changes need unit tests and, when behaviour on the wire changes, a
  test against `FakeTv` (`protocol/src/testFixtures`). UI changes should keep the Robolectric UI tests green.
- **Strings live in resources.** Add every user-visible string to `values/strings.xml` and to
  `values-iw/strings.xml` (Hebrew uses the legacy `iw` code on purpose). `I18nResourcesTest` checks parity.
- **Check right-to-left.** The D-pad is intentionally always laid out left-to-right.
- **Accessibility**: give interactive elements content descriptions and at least 48dp touch targets.

## Before opening a pull request

```sh
./gradlew ktlintFormat   # auto-format
./gradlew check          # tests + lint + ktlint + detekt, must be green
```

Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`,
`chore:` ...) and fill in the pull request template.

## Translations

Translations are plain Android string resources, so they can be moved to Weblate later. Until then, send
a pull request adding `values-<code>/strings.xml` (use Android's legacy codes `iw`, `in`, `ji`) and
add the language to `app/src/main/res/xml/locales_config.xml` and `localeFilters` in `app/build.gradle.kts`.

## Reporting device problems

Please use the *Bug report* or *Device compatibility report* templates and include the TV model and the
**Android TV Remote Service** version (Settings > Apps > See all apps > Android TV Remote Service).
