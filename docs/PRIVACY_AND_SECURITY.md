# Privacy and security

## What the app does with your data

**Nothing leaves your local network.** The app talks only to your TV, on your Wi-Fi.

- No analytics, no advertising, no trackers, no accounts, no cloud services, no Google Play Services.
- No data is collected. There is no server to send it to.
- The **diagnostics log** is stored only on the phone and leaves it only if you press *Share* or *Copy* yourself.
  It never contains pairing codes, keys, typed text or key presses; the last number of an IPv4 address is hidden.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | Android requires it for any network socket, including local ones; the app only connects to your TV |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Network checks for discovery and reconnecting |
| `ACCESS_LOCAL_NETWORK` (Android 17+) | Android requires your permission before an app can find and talk to devices on the local network |

## How pairing and the connection are secured

- **Pairing (port 6467):** TLS with a self-signed RSA client certificate generated on first launch. After the
  request / options / configuration handshake the TV shows a code; the app answers with
  `SHA-256(client key, TV key, code)`.
- **Pinning:** after pairing, the TV's public key is **pinned**. A connection to anything presenting a different
  key is refused, and the app says so ("security certificate changed") instead of connecting silently.
- **Remote session (port 6466):** TLS with the same client certificate.
- **Keys at rest:** the client private key is encrypted with an Android Keystore key and excluded from backups
  (`allowBackup=false` with explicit extraction rules). A restored backup would be useless anyway, because pairings
  are bound to the device identity.
- **Certificate verification is never weakened** to make something work; the trust managers only record or pin.

## Limits you should know

- Anyone on your Wi-Fi who can reach the TV can try to pair, but the TV shows a code that must be typed on the
  phone, which is the protocol's own protection.
- The protocol is informally documented and vendor behaviour differs; see [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
- Early releases are signed with a one-off key; read [INSTALL.md](INSTALL.md) and check the SHA-256 shown in the
  release notes.

Found a vulnerability? Follow [SECURITY.md](../SECURITY.md) and report it privately.
