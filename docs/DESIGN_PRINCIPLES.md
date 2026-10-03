# Design principles

The visual design will change over time. These rules describe how the app should behave and feel whatever it looks
like, so redesigns do not undo what users rely on. Each rule says what it means in practice and where it is enforced.

## 1. The remote is the product

The screen exists to press keys. Status, settings and diagnostics are secondary and must never push the keys off
screen or make them smaller.

- The connection card is **one line** by default.
- The D-pad and transport keys stay in the same place, so muscle memory works in a dark room.

### Layout rules for the keys

- **Look like the object people know:** the keys sit on a centered remote body, in the places a physical TV remote
  uses (mute and power on top, D-pad in the middle, VOL and CH rockers on both sides, app keys at the bottom). The
  layout is symmetric; nothing important hangs on one side only.
- **Aligned columns:** keys line up in three columns from row to row, so the eye and the thumb find them again.
- **Color only where it means something:** OK and the keyboard key carry the accent; power is red; the mute key turns
  red while muted; app keys use their own colors. Everything else is quiet.
- **State shows on the key itself:** the keyboard key lights up when the TV asks for text; mute shows when muted.
- **Physical places are fixed:** the same key is always in the same place, in every language.

## 2. Compact first, details on demand

Show the three facts a person looks for (is it connected, is the TV on, volume and app) and keep everything else one
tap away. Details never rearrange the keys unexpectedly: the card grows, the remote scrolls.

*Where:* `StatusCard` (closed and open states), `StatusCardTest`.

## 3. Problems and their fixes are never hidden

When something is wrong the user must see it and the way out, without opening anything: *Reconnect*, *Pair again*,
the permission prompt, a clear message when nothing was found or a send failed.

*Where:* `ProblemActions` in `StatusCard`, `DiscoverScreen`, keyboard status messages.

## 4. Only say what is true

The app shows only what the TV reported. A missing volume, model or app name is left out, not guessed or shown as
`unknown`. "Sent" means "written to the connection", not "applied by the TV", and the wording must not claim more.
The documentation follows the same rule: a device is "verified" only with recorded evidence
([COMPATIBILITY.md](COMPATIBILITY.md)).

## 5. Never lose the user's work

- A typed draft is kept until the send is known to have succeeded.
- A key held down is released when the app is paused or the touch is lost.
- Failures keep the data and say what to do next.

*Where:* `KeyboardDraftController`, `KeyGestures.releaseAll`.

## 6. Feel native

- Follow the system: light/dark theme by default, wallpaper colors (Material You) with the option to turn them off.
- Controls are at least **48 x 48 dp** with 8 dp between them, also on a small phone. This is tested (`TouchTargetTest`, `RemoteLayoutTest`).
- On wide screens the content stays a centered, readable column.
- Standard Material components and behaviours (back, navigation, snackbars, sheets), no custom gestures to learn.

## 7. Right-to-left done right

In Hebrew the layout mirrors, but the D-pad does **not**: "left" always means the physical left, because it mirrors
the TV's spatial navigation. Brand names keep their direction.

*Where:* `DPadTest` (left-to-right and right-to-left), [DECISIONS.md](../DECISIONS.md) D17.

## 8. Quiet and private

- Nothing is searched or connected on the network without a reason: searching is started by the user and ends by
  itself; the connection is dropped shortly after the app leaves the screen.
- Nothing leaves the phone. The diagnostics log has no secrets and is shared only by the user.

*Where:* `DiscoverViewModel`, `RemoteController`, [PRIVACY_AND_SECURITY.md](PRIVACY_AND_SECURITY.md).

## 9. Accessible by default

Every control has a meaningful label and role; state changes are announced; text scales; nothing depends on color
alone. New screens must work with TalkBack and the largest font before they are merged.

## For contributors

When you change the look, keep these rules and keep the tests that enforce them green. If a rule has to change,
change it here in the same pull request and explain why. Screenshots are deliberately **not** committed to the
repository while the design is moving; the UI tests render screens into `app/build/screenshots/` for review.
