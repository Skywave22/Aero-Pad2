# BluePilot Remote v3

Turn your Android phone into a **Bluetooth keyboard, mouse, media remote, presenter and gamepad** for any PC, laptop, TV or device that accepts Bluetooth input — with a fully customizable, widget-based control system.

Built 100% in Kotlin + Jetpack Compose, MVVM + Clean Architecture, Hilt, Room, DataStore. No account, no cloud, no ads — input goes straight over Android's Bluetooth HID Device API.

## Features

### Core controls
- **Mouse** — trackpad with tap-click, double-tap, long-press right-click, scroll strip, L/M/R buttons; sensitivity, smoothing, pen mode, tap-to-click, invert scroll all tunable.
- **PC Keyboard** — text-send bar, shortcuts (copy/paste/cut/select-all/save/undo/redo), F1-F12, arrows + navigation cluster, combos (Alt+Tab, Alt+F4, Win+D, Win+L, Ctrl+Shift+Esc).
- **Numpad** — real keypad usage codes, calculator layout.
- **Multimedia** — play/pause, stop, tracks, volume, mute, brightness.
- **Presenter** — big prev/next, start/from-here, black/white screen, end.
- **Gamepad** — virtual stick + D-pad + ABXY + shoulders; three modes: real HID gamepad, keyboard fallback, mouse+keyboard hybrid; sensitivity + dead-zone tuning.

### Customization engine
- **Layouts** — build your own control screens from widgets: buttons, trackpads, scroll strips, joysticks, sliders, D-pads.
- **Edit Mode / Preview Mode** — drag to move (grid snap), corner handle to resize, undo, live preview.
- **Full styling per widget** — background/content color, opacity, corner radius, shadow, font size, label, emoji icon, visibility.
- **Action binding** — bind any key/chord, media control, mouse click, text string, or macro to any widget (tap AND long-press).
- **Zone splitter** — one tap splits the canvas into combo regions (trackpad + keys, split H/V, 2x2 grid) — any widget type in any zone.
- **Combo profiles** — save unlimited layouts; switch with arrows or swipe in the player.
- **Import/export** — share layouts as `.bplayout.json` files via the system file picker.
- **2 built-in templates** — Media Remote, Trackpad + Keys.

### Macros
- Step sequences: key taps, chords (Ctrl+Alt+Del), text typing, media, mouse clicks, delays.
- Build, reorder, test-play live, save — then bind to any custom button.

### Settings
- Theme (Light/Dark/System), fullscreen, keep screen on, touch vibrations, secure screen (blocks screenshots).
- Mouse, keyboard and gamepad tuning applied live everywhere.

## Requirements
- **Android 10 (API 29) or newer.**
- A phone/ROM that supports **Bluetooth HID Device mode** (most do; some ROMs block it — the app detects and reports this).
- A host that accepts Bluetooth keyboards/mice: Windows, macOS, Linux, Android/Google TV, most smart TVs. iOS/iPadOS: keyboard yes; mouse depends on OS version + AssistiveTouch settings.

## Pairing with a PC (Windows)
1. Open BluePilot → **Connect** → **Prepare PC pairing** (accept discoverability).
2. Windows: Settings → Bluetooth & devices → **Add device** → Bluetooth → select the phone.
3. Accept pairing on **both** sides; wait for **Connected**.
4. Test Keyboard (Space) first, then Mouse.
5. Stuck? Remove the pairing on BOTH sides and retry with BluePilot open.

## Build
```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK
./gradlew :app:testDebugUnitTest    # 103 unit tests
./gradlew :app:connectedDebugAndroidTest   # Compose UI tests (device needed)
```
JDK 17, Android SDK 34. CI (GitHub Actions) runs tests + lint, auto-increments the version, builds both APKs and attaches them to a GitHub Release on every push.

### Release signing (optional)
Add these repository secrets to sign releases with your own key:
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
Without them, CI signs with the debug key (still installable for testing).

## Privacy & security
- No internet permission use, no analytics, no account.
- Backups and data extraction disabled.
- Optional secure-screen mode blocks screenshots/app-switcher previews.
- Foreground service is not exported; HID input goes only to the paired host.

## Architecture
```
ui/          Compose screens + generic WidgetRenderer (data-driven UI)
viewmodel/   MVVM state holders (StateFlow)
domain/      Interfaces + UseCases + pure logic (editor ops, macro engine, pointer math)
data/        Room (layouts/macros/skins) + DataStore (settings) + JSON serializers
hid/         HID descriptors, report builders, char map, crash-proof HidEngine
service/     Foreground HidService
```
Key invariants:
- Every Bluetooth call is exception-guarded — a disconnect can never crash the app.
- Every stored/imported value is validated (`sanitized()`) before use.
- Sealed-class states everywhere; no nullable state soup.
- Pure logic (report builders, editor ops, macro expansion, pointer math) is 100% unit-tested — 103 tests.
