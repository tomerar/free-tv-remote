# Known limitations

What is known not to work, or not to be verified, today. Items marked *verified in code* were confirmed by
reading and exercising the code; none of these were observed on a physical TV.

## A saved TV whose address changed is found again only in some cases

Since v0.1.9, a search recognises a saved TV that moved to a new address by the name it announces on the network, checks
that the device presents the pinned key, and updates the saved address. This does **not** cover:

- TVs added by typing an address (the app never learned their network name);
- TVs saved before v0.1.9 **and** renamed by the user (the original name is the only link); pairing them again once
  records the network name;
- the moment between the address change and the next search: the app keeps trying the old address, shows the
  connection as failing, and the fix is to open *Add a TV* and search (no new pairing is needed);
- two TVs announcing the same name, which is ignored on purpose because guessing could point an entry at the wrong
  device.

How this behaves on real routers and TVs has not been verified on hardware (TESTING.md, H3).

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

## The sleep timer runs on the phone

The timer is an alarm on the phone that connects to the TV at the end and sends the power key. Consequences:

- The phone must be on and on the same Wi-Fi at that time. If not, the TV is not switched off and the result says so.
  A timer built into the TV (for example TCL's *Settings > System > Power & energy > Sleep timer*) does not have this
  limit, but no command to set it over the network is known, so the app does not set it.
- The deadline is an *alarm-clock* alarm (the kind a clock app uses), which Android delivers on time in Doze and
  phone makers do not hold back; while the timer runs the system shows an alarm icon and the time as the next alarm.
  It is exact only when the user allows *Alarms and reminders* (Android 12 and newer). Without it an ordinary alarm is
  used, which Android may run a few minutes late; the sheet says so. If the app process is alive and the alarm is more
  than five seconds late, the app switches the TV off by itself (the TV is never switched twice). Android also limits how many alarms may wake an idle phone, so two
  timers a few minutes apart in deep sleep may not both be on time.
- **Force stop** removes the alarm (Android 15 and newer). Restarting the phone keeps the timer if it has not ended.
- The power key toggles. If someone switches the TV off with the physical remote in the second between the app
  reading "on" and sending the key, the TV is turned on again. This cannot be ruled out from the phone.
- A TV that does not report its power state (no report on the connection) is left alone.
- It is a convenience, not parental control: anyone can switch the TV on again, and the phone can be switched off.
- Behaviour on a locked, idle phone differs between makers (battery savers). Treat the result notification as the
  truth and report your phone model if the TV stays on.
- Not yet verified on real TVs; see [TESTING.md](../TESTING.md), section P.

## Power on

A Wi-Fi remote cannot wake a device whose remote service is unreachable. See
[TESTING.md](../TESTING.md#6-power-on-and-off-documented-limits).

## NSD on Android 14+ not exercised on a device

The discovery bookkeeping (stale callbacks, lost/rediscovered services, closed scans) is covered by unit tests.
The Android-specific wiring (`registerServiceInfoCallback` on API 34+, `resolveService` on API 26-33) has not
been run on devices of those versions.

## v0.1.0 cannot be updated in place

See [RELEASING.md](RELEASING.md#the-v010-release-and-why-it-cannot-be-updated).
