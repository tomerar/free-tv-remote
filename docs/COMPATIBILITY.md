# Compatibility

## In one sentence

Free TV Remote works with **Android TV and Google TV devices** and an **Android phone**. It does not work with LG
(webOS) or Samsung (Tizen) TVs, because those are different platforms with different protocols.

## What has been verified

| Device | Phone | What was checked | By | Evidence |
| --- | --- | --- | --- | --- |
| **TCL Google TV** (exact model not recorded) | Android phone | Pairing with the on-screen code, connecting, navigating, volume, and the live status card (TV on, volume, current app) | The maintainer, hands-on | Informal: screenshots shared during development. **Not** the full checklist |

What this means, and does not mean:

- It is **real hardware**, not the simulator the automated tests use, so the core protocol path is known to work on
  at least one real TV.
- It is **one device**. Vendors differ in small ways (the protocol is only informally documented), so other TVs
  may need fixes.
- The **full hardware checklist** ([TESTING.md](../TESTING.md)) has **not been completed**: text entry in other
  languages, app shortcuts, standby and wake, Wi-Fi loss, the widget and tile, forgotten pairing and the rest are
  not yet recorded. Until a results file exists in [hardware-results/](hardware-results), nothing beyond the
  table above is claimed.

## Expected to work, not yet tested

Any device that implements the **Android TV Remote protocol v2** (the one Google's own remote app uses) should
work:

- Android TV and Google TV televisions (Sony, Philips, Xiaomi, Hisense, TCL, and others)
- Chromecast with Google TV
- NVIDIA Shield TV and other Android TV set-top boxes

"Should" is not "does". The list above becomes a verified list one report at a time.

## What a TV needs

- It runs **Android TV or Google TV** with the *Android TV Remote Service* available (it is built in).
- The phone and the TV are on the **same Wi-Fi network** (no guest-network isolation between them).
- The TV is **on, or in a light standby** where Wi-Fi and the remote service stay up. A TV in deep standby cannot
  receive anything, and a Wi-Fi remote cannot wake it; see "Power on and off" in [TESTING.md](../TESTING.md).
- Ports **6466** (remote) and **6467** (pairing) are reachable on the TV, and mDNS (`_androidtvremote2._tcp`) is
  not blocked if you want automatic discovery. You can always type the IP address instead.

## LG and Samsung

LG TVs run **webOS** and Samsung TVs run **Tizen**. Neither is Android TV, and neither implements the Android TV
Remote protocol, so this app cannot talk to them: pairing would never find a compatible service.

Supporting them would mean writing a **separate client for each platform's own protocol**, with its own pairing,
security and quirks, plus real devices to test on. That is a substantial amount of work and is **not promised**.
The plan is:

1. Finish verifying the Android TV / Google TV path on more devices first.
2. Then research each platform: how pairing works, what is documented, what licence and privacy constraints apply.
3. Only then decide whether it fits the project's rules (open source, no cloud, no tracking).

If you own an LG or Samsung TV and want to help (for example by testing a prototype), say so in an issue.

## Report your device

Open a [compatibility report](../../../issues/new?template=device_compatibility.yml) and include:

- the exact TV model and its **Android TV Remote Service version** (it matters),
- the phone model and Android version,
- the app version (Settings > About) and, if something failed, the log from **Settings > Diagnostics**.

Better still, follow [TESTING.md](../TESTING.md) and add a results file made from the
[template](hardware-results/TEMPLATE.md). A device is listed as verified only with evidence.
