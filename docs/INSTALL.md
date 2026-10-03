# Installing Free TV Remote

Free TV Remote is a normal Android app. It is not on Google Play, so you install the APK file yourself.
It needs **Android 8.0 or newer** and a TV or streaming box running **Android TV / Google TV** on the same
Wi-Fi network as your phone.

## Option 1: download the APK (recommended)

1. On your phone, open the project's **[Releases](https://github.com/tomerar/free-tv-remote/releases)** page
   and download the latest `FreeTVRemote-<version>.apk` (under *Assets*).
2. Open the downloaded file (from the notification, or from the *Downloads* folder in your Files app).
3. Android will say it blocks installs from this source. Tap **Settings** and switch on
   **Allow from this source** for the app you used (Chrome, Files, ...). Go back.
4. Tap **Install**, then **Open**.
5. Follow the in-app steps: choose your TV, enter the 6-character code shown on the TV. Done.

Some phones show a "Play Protect" warning for apps that are not from the Play Store. Choose
**Install anyway**: the project is open source and you can inspect or build everything yourself.

### Verify the download (optional)

Each release also contains `FreeTVRemote-<version>.apk.sha256`. On a computer, in the folder with both files:

```sh
sha256sum -c FreeTVRemote-<version>.apk.sha256      # Linux
shasum -a 256 -c FreeTVRemote-<version>.apk.sha256  # macOS
```

It should print `OK`.

### Updating

Install the newer APK over the old one. Android only accepts an update signed with the **same key** as
the installed version, so if an update is refused, uninstall the app first (you will have to pair again).

## Option 2: latest development build

Every push runs CI. Open the repository's **Actions** tab, pick the newest green run and download the
`debug-apk` artifact (a zip containing `app-debug.apk`; you must be signed in to GitHub). Debug builds are
larger and may be less polished. Installing works the same way as above.

## Option 3: install with a computer (adb)

Enable *Developer options* and *USB debugging* on the phone, connect it, then:

```sh
adb install FreeTVRemote-<version>.apk
```

## Option 4: build it yourself

```sh
git clone https://github.com/tomerar/free-tv-remote
cd free-tv-remote
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
```

You need JDK 17+ and the Android SDK (platform 37, build-tools 36). See the [README](../README.md#building).

## First launch checklist

- Phone and TV on the **same Wi-Fi** (not a guest network that isolates devices).
- On Android 17 and newer, allow the **local network** permission when asked. Without it the app cannot
  find or control the TV.
- Switch the TV on. The TV shows a code when you pick it in the app.

## F-Droid

F-Droid support is planned; the store metadata already lives in `fastlane/metadata/android`.

## Something went wrong?

Check [TESTING.md](../TESTING.md) for what is known to work, then open an issue with the *Bug report*
template and include your TV model and its *Android TV Remote Service* version.
