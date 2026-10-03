# Known limitations

What is known not to work, or not to be verified, today. Items marked *verified in code* were confirmed by
reading and exercising the code; none of these were observed on a physical TV.

## A saved TV is not found again after its IP address changes (verified in code)

A saved TV connects only to the address stored when it was paired. `TvRepository.updateHost()` exists but
nothing calls it, and discovery runs only on the "Choose your TV" screen. If the router gives the TV a new
address, the remote stays on "Reconnecting" indefinitely and offers no way to edit the address.

Workaround today: remove the TV (Settings > My TVs) and add it again. Pairing again from the discovery list
creates a new entry because entries are matched by address, so the old entry must be removed by hand.

**Safe approach (not implemented):** after several consecutive "unreachable" outcomes in the foreground, run
discovery for a few seconds and, for each candidate, open a TLS connection **verified against the pinned key of the
saved TV** (no commands sent). Only a candidate that presents that exact key may replace the stored host
(`updateHost`). The mDNS name is never trusted as identity, certificate checks stay strict, and no duplicate is
created because the existing entry is updated in place. Needs a small "probe" API in the protocol module, a
discovery lifecycle in `RemoteController`, the local-network permission check, and tests against `FakeTv`.
Also worth adding: after N failed reconnects show "its address may have changed" with a shortcut to re-add the TV.

## Navigation keys are dropped silently while disconnected (verified in code)

`pressKey`/`keyDown`/`keyUp` return `false` when there is no live connection and the UI ignores the result. The
status card at the top of the remote screen shows the connection state (and a Reconnect / Pair again button),
and shortcuts and text now report when they were not sent, but the D-pad and other keys give no per-press
feedback, and the haptic tick confirms the touch, not delivery. A follow-up could dim the controls while
disconnected.

## LG (webOS) and Samsung (Tizen) TVs are not supported

They are not Android TV and do not implement the Android TV Remote protocol this app speaks. See
[COMPATIBILITY.md](COMPATIBILITY.md#lg-and-samsung).

## Quick settings tile and widget connect on demand

With the app closed they open a short-lived connection per tap. Taps are serialised (one temporary connection
at a time) and reuse the live session when the app is connected, but every tap after the first still pays a new
TLS handshake, and if the app is foreground and still *connecting* a second temporary connection can overlap
with it. Whether a given TV drops an older remote connection when a new one arrives has not been checked on
hardware (see TESTING.md, K4). A short idle reuse window for the temporary connection would be a follow-up.

## Protocol behaviour that is only verified against the simulator

Pairing, connecting, navigation keys and volume/power status have been seen working on one TCL Google TV (informal
check, see [COMPATIBILITY.md](COMPATIBILITY.md)). Text entry (counters learned from the TV's IME messages), feature
negotiation, app links and standby recovery still follow the community's description of the protocol and `FakeTv`,
not observed firmware. Whether
"sent" means "applied by the TV" is unknown: the app only knows that the write succeeded.

## Status card details depend on what the TV reports (unverified on hardware)

The compact line (connection, power, volume, app) and the opened details (model, address, connection time) use what
the TV sends. Whether a given TV sends the readable app name (`label`), the model and vendor, or the text-field
request that drives the "The TV is asking for text" shortcut is **not verified on hardware**. Facts the TV does not
send are left out, never guessed. The text-field hint is best effort: the protocol has no known "text field closed"
message, so the hint is cleared when the foreground app changes or the connection restarts, and it can stay on
screen after the TV's keyboard was closed. The keyboard button in the top bar always works regardless. Settings >
Diagnostics records (without any content) when the TV sends an app label or a text-field request, to find out.

## "No longer paired" is inferred, not reported (unverified on hardware)

The session reports `NOT_PAIRED` after two connections in a row that the TV closes before the handshake
completes. That is how an unknown client is treated by a TV, but a TV that is waking up or restarting its remote
service could plausibly do the same and make the app show "no longer paired". Manual Reconnect clears it, and
nothing is deleted, but whether real TVs trigger this has not been observed (see TESTING.md, G1/G2/M1).

## Power on

A Wi-Fi remote cannot wake a device whose remote service is unreachable. See
[TESTING.md](../TESTING.md#6-power-on-and-off-documented-limits).

## NSD on Android 14+ not exercised on a device

The discovery bookkeeping (stale callbacks, lost/rediscovered services, closed scans) is covered by unit tests.
The Android-specific wiring (`registerServiceInfoCallback` on API 34+, `resolveService` on API 26-33) has not
been run on devices of those versions.

## v0.1.0 cannot be updated in place

See [RELEASING.md](RELEASING.md#the-v010-release-and-why-it-cannot-be-updated).
