# Testing: what is proven, and how to prove the rest on real hardware

## 1. What the automated tests do and do not prove

| Evidence | What it can show | What it cannot show |
| --- | --- | --- |
| **Automated tests** (`./gradlew check`): protocol unit tests, end-to-end tests against the in-process `FakeTv`, app/ViewModel tests, Robolectric UI tests, lint | The code does what its authors intended: framing, pairing secret, TLS pinning, message encoding, reconnect and lifecycle logic, UI behaviour, localisation | That real TV firmware agrees. `FakeTv` was written by the same people, from the same assumptions, as the client. Client and simulator can agree with each other and still differ from a vendor's Android TV Remote Service |
| **Physical-device evidence** (this document) | Whether pairing, feature negotiation, keys, text entry, volume, app links, standby recovery, widgets and the tile work on a specific TV, phone and software version | Anything about devices you did not test |

**Status of hardware evidence.** No full-checklist result file has been recorded yet. The maintainer has tried the
app by hand on **one TCL Google TV** with an Android phone (pairing, connecting, navigating, volume and the status
card worked); that informal check is reported in [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) and is **not** a
substitute for this checklist. Beyond that single device the app must not be described as compatible with, or
tested on, any particular TV, and nothing is listed as verified without a results file in
[`docs/hardware-results/`](docs/hardware-results).

## 2. Rules for recording results

- Every item gets exactly one result: **pass**, **fail**, **not supported** (the TV or feature cannot do it:
  say what you saw), or **not tested**. An item you did not try stays *not tested*; never guess.
- A **pass** needs a short note of what you observed (and a screenshot/screen recording for anything
  surprising). A **fail** or **not supported** needs a note describing what happened instead.
- Record the *exact* build and devices (section 4). One results file per device and build.
- Do not edit a results file after the fact to make it look better; add a new file for a new build.

## 3. Priority devices

First targets, in this order. Others are welcome; see the README compatibility table.

1. **TCL Google TV** (record the exact model)
2. **NVIDIA Shield TV** (record the exact model/year)

## 4. Before you start: record the environment

Copy [`docs/hardware-results/TEMPLATE.md`](docs/hardware-results/TEMPLATE.md) to
`docs/hardware-results/<date>-<device>-<app-version>.md` and fill in the header.

| Field | Where to find it |
| --- | --- |
| TV / device exact model | Sticker on the device or Settings > Device Preferences > About > Model |
| TV OS version (Android TV / Google TV) and build | Settings > Device Preferences > About > Version / Build |
| **Android TV Remote Service version** | Settings > Apps > See all apps > show system apps > *Android TV Remote Service* > version (bottom of the page). On Shield the path is the same under Apps |
| Phone model and Android version | Phone Settings > About phone |
| App version **and commit** | App > Settings > About: shows `version (commit)`, e.g. `0.1.1 (a1b2c3d4e)`. For a Release APK also note the release tag and the `.sha256` |
| APK origin | Release asset, CI artifact or local build (development builds and releases are signed differently, see [docs/RELEASING.md](docs/RELEASING.md)) |
| Network | Phone and TV on the **same** Wi-Fi; not a guest network with client isolation. Note the router/AP if anything odd shows up |

## 5. Procedure

Use the IDs in your results file. "Record" means: write down what you actually observed, including timings.

### A. Discovery and manual address
| ID | Steps | Expected / record |
| --- | --- | --- |
| A1 | Fresh install, open the app with no TV saved | Opens on "Choose your TV" with a "Search for TVs" button; nothing is searched until it is tapped |
| A2 | Android 17+ only: permission card, tap Allow; then repeat after denying once | System dialog appears; after a denial "Open app settings" works and granting there starts the search |
| A3 | Tap "Search for TVs" and wait | A progress bar runs for 15 s; the TV is listed within that time with a sensible name and IP, and the search stops by itself. Record the time and the name shown |
| A4 | "Search again" | List refreshes without duplicates. With the TV off: "No TVs found" with hints and the manual IP entry opened |
| A5 | Manual entry: invalid text; a wrong IP; the TV's real IP | Invalid input is rejected with a message; wrong IP leads to a clear "could not reach" state; real IP proceeds to pairing |
| A6 | Phone Wi-Fi off while searching | A clear message, no crash |
| A8 | Open *Add a TV* with one TV saved, before searching | The saved TV is listed at the top and opens the remote when tapped; "Available TVs" shows the search button |
| A7 | With a TV already paired, tap it (marked "Paired") in the search results, and also type its IP address by hand | The remote opens at once and that TV becomes the active one; no pairing code is asked for |

### B. Pairing, wrong code, re-pairing
| ID | Steps | Expected / record |
| --- | --- | --- |
| B1 | Pick the TV | "Contacting the TV…", then a 6-character code appears on the TV. Record how the code looks (case, characters) |
| B2 | Enter the correct code | "Paired!", remote opens, status "Connected" |
| B3 | Pair again, enter a wrong code (typo) | The app says the code is wrong and lets you retry; then the right code works. Record whether the TV keeps the code on screen |
| B4 | Use "Pair again" on an already paired TV | No duplicate entry in My TVs |
| B5 | Switch the TV off, then try to pair | Clear unreachable error with Try again |
| B6 | Rotate the phone during pairing | Pairing continues |

### C. Navigation, OK, Back, Home, long presses
| ID | Steps | Expected / record |
| --- | --- | --- |
| C1 | Tap each D-pad direction and OK on the home screen | Correct direction (left is really left) |
| C2 | Hold an arrow on a long row | Smooth repetition. Record speed |
| C3 | Back, Home, Menu | Each behaves as expected. Record what Menu does on this TV |
| C4 | Hold OK on an app icon | Context menu opens (a real long press) |
| C5 | Hold Back and Home | Record the TV's reaction (device dependent) |

### D. Volume, mute, phone volume buttons
| ID | Steps | Expected / record |
| --- | --- | --- |
| D1 | Volume −/+ | TV volume changes. Record whether the on-screen TV volume bar appears |
| D2 | Hold volume + | Repeats. Record speed |
| D3 | Mute twice | Mutes and unmutes. Record whether the app's status line shows it |
| D4 | Phone volume buttons on the remote screen | Control the TV, hold repeats; phone volume does not change |
| D5 | Switch the setting off; leave the remote screen | Phone buttons control the phone again |
| D6 | Change TV volume with the TV's own remote | The status card volume text follows, or record that the TV does not report it (e.g. external audio/CEC) |
| D7 | Tap the status card to open it, then close it | Closed: one line (connection, TV on/off, volume, app). Open: model, volume bar, app, address, connection time. Record which facts this TV does **not** send (model, app name, volume) and what the app name shows for the home screen and for a streaming app |

### E. Text entry
Focus a TV text field first (for example the search box of a video app).

| ID | Steps | Expected / record |
| --- | --- | --- |
| E1 | Send `hello world` | Appears in the field |
| E2 | Send `שלום עולם` | Record exactly what appears (correct letters? order? nothing?) |
| E3 | Send an emoji such as `👍` | Record what appears |
| E4 | Send three texts one after another | Record whether text is appended, replaced, or lost |
| E5 | Change to a different TV text field, send again | Goes to the newly focused field |
| E6 | Backspace and Enter buttons | Act in the TV field |
| E7 | Turn phone Wi-Fi off, press Send | Message says nothing was sent and the draft is still in the box; after reconnecting, Send works |
| E8 | Focus a text field on the TV, open the status card | Record whether "The TV is asking for text" and the Type button appear, and whether they go away after the TV's keyboard is closed (known: they may stay, see docs/KNOWN_LIMITATIONS.md) |

### F. App shortcuts
| ID | Steps | Expected / record |
| --- | --- | --- |
| F1 | Tap each built-in shortcut | Opens that app, or record "app not installed" and what the TV shows |
| F2 | Add a custom deep link, then use it | Opens the target |
| F3 | Add an invalid link | Rejected with a message |

### G. Standby and wake
| ID | Steps | Expected / record |
| --- | --- | --- |
| G1 | Put the TV in standby with its own remote | The app shows reconnecting. Record how long until the state changes |
| G2 | Wake the TV with its own remote | The app reconnects by itself. Record the delay |
| G3 | Press Power in the app while the TV is on | Goes to standby (record) |
| G4 | Press Power in the app while the TV is in standby | Record whether it wakes the TV or whether the key is not delivered (see section 6) |
| G5 | Leave the TV off or in deep sleep for 10 minutes, then try | Record whether the app can connect at all |

### H. Wi-Fi loss and restoration
| ID | Steps | Expected / record |
| --- | --- | --- |
| H1 | Phone Wi-Fi off, wait 20 s, on | The app reconnects without restart. Record time |
| H2 | TV Wi-Fi/network off and on | The app reconnects. Record time |
| H3 | Reboot the router or give the TV a new IP (DHCP reservation change), then open *Add a TV* and search | The saved TV is recognised, marked *On this network*, its address is updated and the remote works without pairing again. Record whether it worked, and what happens for a TV that was renamed (known limitation, see `docs/KNOWN_LIMITATIONS.md`) |

### I. Background and foreground
| ID | Steps | Expected / record |
| --- | --- | --- |
| I1 | Leave the app for 10 s, return | Still connected |
| I2 | Leave it for 2 minutes, return | Reconnects by itself. Record time |
| I3 | Hold an arrow, then lock the phone | The TV does not keep scrolling afterwards |

### J. Rotation and rapid reconnect
| ID | Steps | Expected / record |
| --- | --- | --- |
| J1 | Rotate on the remote screen | Stays connected |
| J2 | Type a draft in the keyboard sheet, rotate | Draft is kept |
| J3 | Switch away and back ten times quickly | Ends up connected, no stuck state |
| J4 | Tap "Reconnect" repeatedly while disconnected | Ends up connected or failed cleanly, no crash |

### K. Widget and Quick Settings tile (app UI closed)
Add both. Swipe the app away from recents before each test.

| ID | Steps | Expected / record |
| --- | --- | --- |
| K1 | Widget: Vol −, Vol +, Mute | TV reacts. Record the delay |
| K2 | Widget: Power | Record the TV's reaction |
| K3 | Tile tap | TV reacts; the tile shows the result |
| K4 | Tap the widget twice quickly, and widget plus tile together | No crash; the TV receives sensible input |
| K5 | TV off or unreachable | A failure message, no crash |

### L. Multiple saved devices
| ID | Steps | Expected / record |
| --- | --- | --- |
| L1 | Pair a second TV | Both appear in the TV menu and in My TVs |
| L2 | Switch between them | Keys go only to the selected TV |
| L3 | Rename and remove | Work; removing the active TV disconnects cleanly |
| L4 | Force-stop and reopen | Reconnects to the last used TV |

### M. Forgotten pairing and certificate problems (where practical)
The menu path to forget a paired phone differs per vendor (often *Android TV Remote Service > Clear data*, or
*Remotes & accessories*). Record the path you used.

| ID | Steps | Expected / record |
| --- | --- | --- |
| M1 | Make the TV forget this phone | The app reports it is no longer paired and offers "Pair again". Record which message appeared |
| M2 | Pair again | Works |
| M3 | If clearing the TV's remote service data changes its certificate: reconnect | Record whether the app says "no longer paired" or "security certificate changed", and that it never connects silently |
| M4 | Not practical on most setups: a different device at the saved IP | If you can arrange it: must be refused. Otherwise leave *not tested* |

### N. Languages, themes, accessibility
| ID | Steps | Expected / record |
| --- | --- | --- |
| N1 | Phone language Hebrew | Mirrored layout, **D-pad left still moves left**, rewind still on the left |
| N2 | TalkBack on | Every control is announced; D-pad directions activate by double tap |
| N3 | Largest font | Nothing cut off, the screen scrolls |
| N4 | System / Dark / Light themes | All readable |

### O. Privacy spot check (optional)
| ID | Steps | Expected / record |
| --- | --- | --- |
| O1 | Watch router logs or a network monitor while using the app | Only the TV (ports 6466/6467) and mDNS |

## 6. Power on and off: documented limits

The app sends the same power key as the official remote, over Wi-Fi, to the TV's *Android TV Remote Service*.
That only works while that service is reachable.

- If the TV is **fully off, or in a deep standby where Wi-Fi and the remote service are shut down**, there is
  nothing to receive the command. A Wi-Fi remote **cannot wake such a device**, and the app does not claim it
  can. In that state the app shows the TV as unreachable and keeps retrying.
- Many TVs keep the service available in light standby, and a power key may then wake them. This differs by
  manufacturer, model and settings (for example "Wake on network / Wi-Fi" options), so it must be recorded
  per device in items G3 to G5, not assumed.
- Wake-on-LAN is not implemented; it is listed as an idea in `docs/ISSUES.md`.

## 7. Reporting

Send the results file in a pull request, or open a *Device compatibility report* issue (template in the
repository) and paste the filled header plus every item that is not a plain pass. Compatibility claims in the
README are added only from recorded evidence.
