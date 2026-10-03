# Manual test checklist (real devices)

Automated tests run against a simulated TV only. **This checklist is what proves the app works on real
hardware.** Run it on any Android TV / Google TV device you own and report the results with the *Device
compatibility report* issue template, so the compatibility table in the README can grow.

Record first:

| | Device A | Device B (optional) |
| --- | --- | --- |
| TV model | | |
| Android TV / Google TV version | | |
| Android TV Remote Service version (Settings > Apps > See all apps > show system apps) | | |
| Phone model + Android version | | |
| App version (Settings > About) | | |

Install the debug or release APK from CI / Releases. Phone and TV must be on the same Wi-Fi (not a guest
network with client isolation). Mark each line ✅ / ❌ / n/a and write a note for every ❌.

## 1. First run and discovery
- [ ] App opens on the "Choose your TV" screen when no TV is saved
- [ ] On Android 17+: the local network permission card appears; "Allow" shows the system dialog
- [ ] After denying: "Open app settings" appears and works; granting there and returning starts the search
- [ ] The TV appears in the list within ~10 s, with a sensible name and IP
- [ ] "Search again" refreshes the list
- [ ] Manual IP entry: invalid text shows an error; the TV's real IP continues to pairing
- [ ] Airplane mode / Wi-Fi off: a clear message, no crash

## 2. Pairing
- [ ] Tapping the TV shows "Contacting the TV…", then a code appears **on the TV**
- [ ] The code field accepts only hex characters, upper-cases input and limits to 6
- [ ] A wrong code: the app says it is wrong and lets you retry (note: does the TV still show the code?)
- [ ] The correct code: "Paired!" and the remote screen opens, status turns "Connected"
- [ ] Rotating the phone during pairing does not cancel it
- [ ] Switching the TV off before entering the code: clear "could not reach" error with Try again
- [ ] Pairing the same TV again (after "Pair again") does not create a duplicate entry

## 3. Remote screen
- [ ] OK, Up, Down, Left, Right navigate the TV home screen correctly (Left is really left)
- [ ] Holding an arrow repeats smoothly (scrolling a long row)
- [ ] Holding OK on an app icon opens the context menu (long press) ; tapping OK launches
- [ ] Back, Home, Menu behave correctly
- [ ] Volume −/+ change TV volume; holding repeats; Mute toggles
- [ ] Power turns the TV off (note how long until it responds) and, if it can, back on
- [ ] Play/pause, rewind, fast forward work in a video app (YouTube/Netflix)
- [ ] Haptic feedback on button press; off when disabled in Settings
- [ ] Status card shows power state, volume and the current app name/package
- [ ] Phone volume buttons control the TV only on the remote screen; turned off in Settings they control the phone again; holding repeats
- [ ] "Keep screen on" keeps the phone awake while the remote is open
- [ ] Rotation does not disconnect or lose state
- [ ] Press and hold a key, then lock the phone: the TV does not keep "holding" the key

## 4. Keyboard
- [ ] Open a TV search box, open the keyboard sheet, type "hello", Send: text appears in the TV field
- [ ] Send a second text: it is appended (or replaces?) — note actual behaviour
- [ ] Backspace and Enter buttons act in the TV field
- [ ] Non-Latin text (e.g. Hebrew, emoji): note what happens

## 5. App shortcuts
- [ ] Netflix, YouTube, Disney+, Prime Video, Spotify, Plex: each opens the app on the TV (note any that do not, and whether the app is installed)
- [ ] Settings > App shortcuts: turn one off (disappears from the remote), reorder, add a custom link such as `https://www.youtube.com/watch?v=dQw4w9WgXcQ`, delete it
- [ ] An invalid link is rejected with a message

## 6. Multiple TVs
- [ ] Pair the second TV; both appear in the TV menu (top left of the remote) and in Settings > My TVs
- [ ] Switching connects to the other TV and keys go to that TV only
- [ ] Rename and remove work; removing the active TV disconnects cleanly
- [ ] Force-close and reopen: reconnects to the last used TV automatically

## 7. Resilience
- [ ] Put the TV into standby with its own remote: the app shows reconnecting; when the TV wakes, it reconnects by itself
- [ ] Unplug the TV, wait a minute, plug in: reconnects
- [ ] Turn phone Wi-Fi off and on during use: reconnects, no crash
- [ ] Background the app for >30 s, return: reconnects quickly
- [ ] In TV settings remove the paired device (Android TV Remote Service > Clear data, or factory reset): the app says "no longer paired" and offers "Pair again"
- [ ] A different TV at the same IP (or a factory-reset TV): "security certificate changed" message, never connects silently

## 8. Tile and widget
- [ ] Add the "TV power" Quick Settings tile; tapping toggles TV power; subtitle shows result
- [ ] Add the widget to the home screen; Power, Vol −, Vol +, Mute work **with the app closed**
- [ ] With the TV off/unreachable: a failure message, no crash

## 9. Languages, themes, accessibility
- [ ] Switch phone language to Hebrew (and per-app language on Android 13+): all screens in Hebrew, mirrored layout, **D-pad Left still moves left**, rewind still on the left
- [ ] "Disney+" shows correctly in Hebrew
- [ ] Theme: System / Dark / Light all readable
- [ ] TalkBack on: every button is announced, the D-pad directions can be activated by double tap
- [ ] Largest font size: nothing is cut off, the screen scrolls

## 10. Privacy check (optional)
- [ ] With a network monitor / router logs: the phone only talks to the TV (ports 6466/6467) and does mDNS
