# Features

## Finding and pairing a TV

- **Search on request.** Nothing listens on the network until you tap *Search for TVs*. The search runs for 15
  seconds with a progress bar and then stops by itself, so it does not drain the battery. Results appear as they
  are found; tapping one stops the search.
- **Clear outcomes.** If nothing is found the app says so, lists what to check (same Wi-Fi, TV on, permission), and
  opens the manual entry. Manual entry accepts an IPv4 address or a host name.
- **Already paired? No second pairing.** Tapping a TV marked *Paired* (or typing its address) selects it and opens the
  remote right away. Pairing again is offered only when the connection reports it is needed (*Pair again*).
- **Pair once.** The TV shows a 6-character code; type it in. A mistyped code lets you try again without starting
  over. The TV's public key is pinned afterwards, so another device cannot impersonate it.
- **Local-network permission.** On Android 17 and newer the app asks for access to devices on the local network and
  explains why.

## The remote

- **Circular D-pad** built from four sectors plus a centre OK button. Each sector is its own accessibility
  element. Arrows repeat while held, OK and others support a real long press.
- **Keys:** Back, Home, Menu, power, mute, volume up/down, play/pause, rewind, fast forward.
- **Phone volume buttons** control the TV volume while the remote is open (optional).
- **Haptic feedback** and optional **keep screen on**.
- **Right-to-left safe:** in Hebrew the layout mirrors, but "left" on the D-pad is always physically left.

## The status card

One compact line: **connection · TV on/off · volume · current app**, for example
`Connected · TV on · Volume 13 · Netflix`. Tap it to open the details:

- TV vendor and model, a volume bar, the current app, the TV's address and how long you have been connected;
- a **Type** shortcut when the TV asks for text input;
- a link to **Diagnostics**.

Problems and their actions (permission, *Reconnect*, *Pair again*) are never hidden behind the tap. Every fact is
shown only if the TV reported it. The app name is the TV's own name when it sends one, else a built-in list, else
(in the details only) the package id.

## Typing on the TV

Write on the phone keyboard and send. The draft is kept until the send is known to have succeeded, so a failed
send never loses your text. Backspace and Enter buttons act in the TV's field.

## App shortcuts

Built-in shortcuts for Netflix, YouTube, Disney+ and Prime Video use deep links; you can add your own
(`scheme://...`), reorder, disable or delete them. The app tells you when a shortcut could not be sent.

## Several TVs

Save as many TVs as you like, switch from the title menu, rename or remove them. The app reconnects to the last used
one on start.

## Quick Settings tile and widget

Power and volume without opening the app. They connect on demand and disconnect afterwards.

## Appearance

- **Material You** colors from your wallpaper (Android 12+, can be switched off), otherwise the teal theme.
- **System / dark / light** theme.
- On tablets and unfolded screens the app stays a centered column.
- **English and Hebrew.**
- Every tappable control is at least 48 x 48 dp (checked by a test), with TalkBack-friendly labels.

## Diagnostics

**Settings > Diagnostics** keeps a rolling log on the phone (up to 10 MB in two files): searches, pairing steps,
connection changes and errors or crashes. **Copy log** puts the newest 100 KB on the clipboard; **Share** sends the
whole log as a file. It never contains pairing codes, keys, typed text or key presses, and the last number of an
IPv4 address is hidden. Nothing leaves the phone unless you share it.

## Resilience

Reconnects with backoff, copes with TV standby, Wi-Fi loss and rotation, never leaves a key held down on the TV when
the app is paused, and closes connections off the main thread. Known gaps are listed honestly in
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
