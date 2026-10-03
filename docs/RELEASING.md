# Releasing (maintainers)

Android decides whether a new APK may update an installed app by its **signing identity**. A release that is
signed with a different key than the installed one cannot be installed as an update. Therefore:

- **Published releases must always be signed with the same stable project key.** The release workflow refuses
  to publish otherwise; there is no fallback to a generated key.
- **Development builds** (manual workflow run without a tag, or any local build) use throw-away or debug keys.
  They install fine but can never update, or be updated by, a published release. They are never published.

## One-time setup: create and store the project key

Do this once, on your own machine, and keep the key forever. **If the key is lost, every installed copy of the
app can no longer be updated** (users would have to uninstall and pair their TVs again).

```sh
keytool -genkeypair -v -keystore free-tv-remote-release.jks -alias release \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Free TV Remote, O=Free TV Remote contributors"
```

1. Back the `.jks` file and its passwords up in at least two safe places (e.g. a password manager and an
   encrypted offline copy). **Never commit it.** `*.jks`, `*.keystore` and `keystore.properties` are git-ignored.
2. In the GitHub repository, *Settings > Secrets and variables > Actions*, add these **secrets**:

   | Secret | Value |
   | --- | --- |
   | `SIGNING_KEY_BASE64` | `base64 -w0 free-tv-remote-release.jks` (macOS: `base64 -i file \| tr -d '\n'`) |
   | `SIGNING_STORE_PASSWORD` | the keystore password |
   | `SIGNING_KEY_ALIAS` | `release` (or your alias) |
   | `SIGNING_KEY_PASSWORD` | the key password |

3. Print the signer fingerprint (public information) and store it as a **variable** (not a secret) named
   `RELEASE_CERT_SHA256`. The workflow then refuses to publish if the key ever differs:

   ```sh
   keytool -list -v -keystore free-tv-remote-release.jks -alias release | grep 'SHA256:'
   ```

   (Colons and case do not matter.) Do not use a certificate subject that contains "CI build", "DEVELOPMENT
   build" or "local build": the workflow treats those as throw-away identities and rejects them.

## Cutting a release

1. Bump `versionCode` (must increase with every release, or Android refuses the update) and `versionName` in
   `app/build.gradle.kts`, update the changelog (`fastlane/metadata/android/*/changelogs/<versionCode>.txt`),
   merge to `main` and wait for a green CI.
2. Publish, either by pushing a tag `v<versionName>` or *Actions > Release > Run workflow* with that tag.
   The workflow checks that the tag equals `versionName`, builds and tests, signs with the project key,
   verifies the signer (and `RELEASE_CERT_SHA256` if set), and creates the GitHub Release with the APK and its
   `.sha256`. If a secret is missing it stops immediately with a message naming what is missing.

A manual run with the tag left **empty** only produces a development APK (artifact, not a release).

### Publishing without the stable key (explicit opt-in, not recommended)

If you have not set up the project key yet, a **manual** run with a tag and `allow_unstable_signing = true` publishes
a release signed with a one-off key. The release notes then carry a bold warning, because Android cannot update such
an app in place from or to any other release: people must uninstall it (losing their saved TVs and pairing) before
installing the next one. Tag pushes never get this fallback and always need the stable key. Switch to the stable
key as early as possible; every release signed with a one-off key is a dead end for the people who installed it.

## Checking update compatibility yourself

Signer of any APK (`apksigner` is in the Android SDK `build-tools`):

```sh
apksigner verify --print-certs FreeTVRemote-v0.1.1.apk | grep 'SHA-256'
```

Two APKs can update each other only if their signer SHA-256 is identical **and** the newer `versionCode` is
higher. The definitive check is on a device or emulator: install the old APK, then
`adb install -r new.apk` must succeed without uninstalling first.

## Local builds

Without any signing configuration `./gradlew assembleRelease` produces an **unsigned** APK (not installable).
To sign locally, create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`, or set the `SIGNING_*` environment variables. A *partial* configuration always fails. To make a
local build enforce the same rule as the release workflow, set `REQUIRE_RELEASE_SIGNING=true` or pass
`-PrequireReleaseSigning=true`; the build then stops immediately if the configuration is incomplete.

## The v0.1.0 release and why it cannot be updated

`v0.1.0` was published before this safeguard existed. Its APK is signed with a **one-off key that the release
workflow generated on a temporary CI runner** (certificate `CN=Free TV Remote CI build`, SHA-256
`a225f5862df575232eef1ec2243bf9ba2f16154a274255a3f2e5f808003eeadc`). That key was never stored anywhere and
cannot be recovered, so it is impossible to sign a later release with it. Android's APK signature key rotation
needs the old key to sign the rotation proof, so there is **no supported migration path** for that
installation: the first release signed with the stable key can only be installed after uninstalling v0.1.0.
Uninstalling deletes the app's data (saved TVs, settings and the phone's pairing identity); the TVs have to be
paired again, and the old phone entry can be removed from the TV's *Android TV Remote Service* settings.
It is reasonable to mark `v0.1.0` as a pre-release / deprecated in its release notes.
