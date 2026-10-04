# Features

## Finding and pairing a TV

- **Search on request.** Nothing listens on the network until you tap *Search for TVs*. The search runs for 15
  seconds with a progress bar and then stops by itself, so it does not drain the battery. Results appear as they
  are found; tapping one stops the search.
- **Clear outcomes.** If nothing is found the app says so, lists what to check (same Wi-Fi, TV on, permission), and
  opens the manual entry. Manual entry accepts an IPv4 address or a host name.
- **Saved and available, like Wi-Fi settings.** The TV screen lists your **saved TVs** at the top, always, even before
  searching; tapping one selects it and opens the remote right away, with no code. A saved TV that answers the search
  is marked *On this network*. Below, **available TVs** are only the new ones that can be paired. Typing the address
  of a saved TV also just opens the remote. Pairing again is offered only when the connection reports it is needed
  (*Pair again*).
- **Finds a TV again when its address changes.** When the router gives a saved TV a new address, the next search
  recognises it by the name it announces on the network and updates the saved address, so you do not pair it again.
  Before changing anything the app checks that the device at the new address presents the key pinned at pairing (a
  TLS handshake only, nothing is sent), so another TV with the same name, for example at someone else's home, can
  never take over a saved entry. TVs paired before v0.1.9 are recognised by their original name unless you renamed
  them; TVs added by typing an address are not recognised this way.
- **Pair once.** The TV shows a 6-character code; type it in. A mistyped code lets you try again without starting
  over. The TV's public key is pinned afterwards, so another device cannot impersonate it.
- **Local-network permission.** On Android 17 and newer the app asks for access to devices on the local network and
  explains why.

## The remote

The remote is drawn as the body of a physical TV remote, centered, with the keys where people expect them. The layout
is the same in every language (left is always left):

| Row | Left | Middle | Right |
| --- | --- | --- | --- |
| 1 | Mute | | Power (red) |
| 2 | Input | | Menu |
| 3 | | **D-pad with OK** | |
| 4 | Back | **Keyboard** (where a remote has its microphone) | Home |
| 5 | TV settings | | Guide |
| 6 | **VOL** rocker (+ / −) | Play/pause, Info | **CH** rocker (up / down) |
| 7+ | App keys, three per row | | |

- Arrows, volume and channel repeat while held; OK and the other keys support a real long press.
- The mute key turns red while the TV is muted. The keyboard key lights up when the TV is asking for text.
- Each D-pad direction is its own accessibility element; every key has a spoken name and is at least 48 x 48 dp.
- **Phone volume buttons** control the TV volume while the remote is open (optional).
- **Haptic feedback** and optional **keep screen on**.
- Channel, guide and info keys depend on the TV and its apps; on a streaming-only setup some of them may do nothing.

## App keys

At the bottom of the remote, a grid of colored circles with one or two letters and the app's name below (Netflix is a
red **N**, Disney+ a blue **D+**, and so on), so each one is found by color like the app keys of a real remote. **No
brand logos are used or shipped**; custom shortcuts get a stable color from their name. The last key edits the list.

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

## Managing shortcuts

Built-in shortcuts for Netflix, YouTube, Disney+ and Prime Video use deep links; you can add your own
(`scheme://...`), reorder, disable or delete them. The app tells you when a shortcut could not be sent.

## Sleep timer

The moon icon at the top of the remote opens the **sleep timer**: the TV switches itself off after the time you choose.

- **Any duration.** Quick choices (15, 30, 45, 60, 90 and 120 minutes), a number box (1 to 720 minutes) and
  + / - buttons that move in steps of five. The sheet shows the duration in words and the time the TV will turn off.
- **See the time left.** While a timer runs, the icon becomes the countdown, the sheet shows a large countdown and a
  progress bar, and a notification keeps counting down outside the app, with *+15 min* and *Cancel* buttons.
  Extend by 5, 15 or 30 minutes, or cancel, at any time before the end.
- **Keeps running when the app is closed.** The end is an alarm set with Android, so the timer survives the app being
  swiped away, the process being killed, and a restart of the phone (when the phone was off at the end, the timer is
  reported as *missed* and the TV is **not** switched off late).
- **Never turns a TV on.** Android TV has one power key that toggles. The app sends it only when the TV itself says,
  on a fresh connection, that it is on. A TV that is already off, does not answer, or does not say its state gets
  nothing, and you are told why. A power command is never sent twice.
- **Says what happened.** The result (*turned off*, *command sent but not confirmed*, *already off*, *could not be
  reached* and so on) appears as a notification and in the sheet.
- **Works with every TV the remote works with.** It uses only the power key that the remote already sends.

It runs on the phone, not on the TV. The phone must be on, with its Wi-Fi connected to the same network, when the
time is up. See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md#the-sleep-timer-runs-on-the-phone).

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
