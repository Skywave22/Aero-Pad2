# BluePilot Remote v3 — Manual Test Checklist

Run before each release. Needs one Android 10+ phone and at least one host per platform section.

## 1. Install & first run
- [ ] APK installs on Android 10, 12, 14.
- [ ] First launch shows Home with status card "Ready/Not connected"; no crash.
- [ ] Connect tile → permission screen appears (Android 12+: Connect/Scan/Advertise; 10-11: Location).
- [ ] Deny permissions → app stays usable, status shows "Permissions needed", no crash.
- [ ] Grant permissions → Connection screen, engine initializes.

## 2. Pairing & connection (each host OS)
### Windows 10/11
- [ ] Prepare PC pairing → phone appears in Add device → pairs both sides.
- [ ] Status shows Connected; notification shows "Connected to <PC>".
- [ ] Keyboard Space/Enter works; mouse moves; scroll works.
- [ ] Turn Bluetooth off on PC → app returns to Ready, auto-reconnect attempts logged; no crash.
- [ ] Re-pair after removing pairing on both sides works.
### macOS
- [ ] Pairs via System Settings → Bluetooth; keyboard + mouse work.
### Linux (BlueZ)
- [ ] Pairs via bluetoothctl/GNOME; keyboard + mouse work.
### Android TV / Google TV
- [ ] Pairs via Remotes & accessories; D-pad/keyboard navigation works.
### iOS/iPadOS (best effort)
- [ ] Keyboard input works; note mouse behavior for the README.

## 3. Control screens (connected to Windows)
- [ ] Mouse: tap=click, double-tap, long-press=right-click, drag=move, scroll strip, L/M/R buttons.
- [ ] Mouse settings: sensitivity 0/50/100 change speed; pen mode slows; smoothing visible; invert scroll flips.
- [ ] Keyboard: text bar sends string incl. capitals + symbols; all shortcut keys verified in Notepad.
- [ ] Numpad: digits + operators + NumLock correct.
- [ ] Multimedia: play/pause, tracks, volume, mute in a media player.
- [ ] Presenter: PgUp/PgDn flip slides; F5 starts; B blanks; Esc ends (PowerPoint).
- [ ] Gamepad HID mode: gamepad visible in joy.cpl; buttons/stick/dpad register.
- [ ] Gamepad keyboard fallback: buttons produce mapped keys.
- [ ] Haptics follow the touch-vibrations setting on every screen.

## 4. Settings
- [ ] Theme Light/Dark/System switches instantly.
- [ ] Keep screen on / fullscreen / secure screen (screenshot blocked) all work.
- [ ] All sliders persist after app kill + relaunch.

## 5. Layouts & editor
- [ ] Built-in templates present; both play correctly (trackpad moves, buttons fire).
- [ ] New layout → editor opens; add each widget type; drag moves with snap; handle resizes; min size enforced.
- [ ] Style: colors, opacity, radius, shadow, font, label, icon, visibility all render live.
- [ ] Action picker: key, media, mouse, text, macro bindings all fire in Preview.
- [ ] Zones: Pad+keys / Split H / Split V / Grid 2x2 each create correct regions.
- [ ] Undo reverts drag/style/add/delete; save persists after app restart.
- [ ] Editing a built-in template saves as a copy; template unchanged.
- [ ] Export layout → JSON file created; import it back → "(imported)" copy appears.
- [ ] Import a garbage file → friendly error, no crash.
- [ ] Player: arrows + swipe on top bar switch between saved layouts.

## 6. Macros
- [ ] Create macro with key + chord + text + delay + media steps; reorder; delete steps.
- [ ] Test-play executes on host with correct order/timing.
- [ ] Save → bind to a layout button → tap runs it; long-press binding also works.
- [ ] Starting a second macro cancels the first (no interleaving).

## 7. Stability & lifecycle
- [ ] Background the app 10 min while connected → connection or auto-reconnect survives (battery optimization off).
- [ ] Kill from app switcher → service notification clears; relaunch reconnects cleanly.
- [ ] Rotate on every screen → no crash, state kept.
- [ ] Toggle phone Bluetooth off/on mid-session → clean state transitions, no crash.
- [ ] Monkey test 5 min (`adb shell monkey -p com.bluepilot.remote 5000`) → no ANR/crash.
- [ ] Logcat clean of: ClassCastException, NullPointerException, IllegalStateException from app code.

## 8. HID-unsupported device (if available)
- [ ] Phone/ROM without HID Device mode shows the "HID not supported" state, app fully navigable.
