# Security policy

## Supported versions

Only the latest release receives security fixes while the project is pre-1.0.

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Use GitHub's private reporting:
open the repository's **Security** tab and choose **Report a vulnerability**. We will acknowledge
within a few days and keep you informed until a fix is released.

## Scope and threat model

- The app communicates only with the TV over the local network, using TLS with a self-signed client
  certificate (as the Android TV Remote protocol requires).
- The TV's certificate is **pinned** (SHA-256 of its public key) at the moment of pairing. A changed
  certificate is refused and surfaced to the user.
- The phone's private key is stored encrypted with an Android Keystore key and is excluded from backups.
- Pairing relies on the code displayed on the TV; anybody who can see the code while a pairing is in
  progress can pair, exactly as with the official remote.

Out of scope: attacks that require a compromised phone or TV, and weaknesses of the Android TV Remote
protocol itself (we will still forward them to the right place if we learn of any).
