# BluePilot Remote v3 — Full Rebuild Plan (from zero)

> Old code (`BluePilot-Remote-main/`) is kept ONLY as a feature reference — nothing is copied blindly.
> Every line in v3 is written fresh, clean, commented, null-safe.
> Priority: STABILITY → FEATURES → AESTHETICS.
> Target: Android 10+ (minSdk 29), targetSdk 34, 100% Jetpack Compose, Hilt, MVVM + Clean Architecture.

---

## FEATURE CONTRACT (nothing from the old app may be lost)

Connection hub (guided pairing) • Devices list • Scanner • Permission flow •
Mouse trackpad (tap/double/long-press-right-click, scroll, smoothing, pen mode) •
PC Keyboard (text bar, F-keys, arrows, modifiers, shortcuts) • Numpad •
Multimedia (play/pause/track/volume/brightness) • Presenter • Gamepad (HID + 2 fallback modes) •
Settings (theme, fullscreen, keep-screen-on, vibrations, secure screen, mouse/kb/gamepad tuning) •
Help/troubleshooting • Foreground HID service • HID-unsupported detection • CI builds APK.

PLUS the new v3 features:
Customization Engine (edit mode, drag/resize/style everything) • Combo zone system •
Macro system & custom buttons • Theme editor / skins • JSON layout import-export • Tests.

---

## MODULES (one per work session — we confirm each before moving on)

### ✅ Module 0 — Project Skeleton  ← WE ARE HERE
Fresh Gradle project (Kotlin DSL + version catalog), Hilt, Timber, Compose theme,
navigation shell, launcher icon, GitHub Actions workflow so EVERY push compiles on CI.
Deliverable: app builds and shows a home shell. Zero warnings.

### Module 1 — HID Core Engine
- All 5 HID report descriptors (keyboard / mouse / consumer / system / gamepad), documented byte-by-byte
- `HidEngine` (rewritten BluetoothHidManager): every BT call wrapped, never crashes on disconnect,
  auto-reconnect with backoff, StateFlow state machine (sealed class states)
- Full keycode tables, HidAction sealed hierarchy, char→HID text typing map
- PermissionManager (API 29–30 legacy path, 31+ new permissions)
- Unit tests for report building & text mapping

### Module 2 — Service + Connection Flow
- Foreground HidService (connectedDevice type, clean notification, wake lock)
- UseCase layer (ConnectDevice, SendHidAction, ObserveConnection…)
- Screens: Permission → Connection hub (guided pairing) → Devices → Scanner
- Defensive: Bluetooth off / permission denied / HID unsupported all handled with clear UI states

### Module 3 — Data Layer
- Room DB (profiles, layouts, macros, skins) + DataStore (simple settings)
- Repositories + kotlinx-serialization JSON models (ready for import/export)
- Settings screen with all v2 toggles/sliders + input validation

### Module 4 — Control Screens (classic set)
- Mouse trackpad, PC Keyboard, Numpad, Multimedia, Presenter, Gamepad, Help
- All driven through UseCases; haptics; settings applied live

### Module 5 — Widget Framework (foundation for customization)
- Widget model: every control (button, key, trackpad, joystick, slider, dpad, macro-button)
  is DATA (position, size, style, action) rendered by one generic renderer
- Theme engine v2: custom color schemes, per-widget style overrides, skins/presets

### Module 6 — Customization Engine
- Edit Mode ⇄ Use Mode toggle
- Drag & drop (free + grid snap), pinch/slider resize
- Style editor: color, opacity, corner radius, shadow, font, icon, label, visibility
- Per-profile layouts in Room; import/export as `.bplayout.json` files (SAF share)

### Module 7 — Combo Zones + Macros
- Zone splitter: horizontal / vertical / grid; assign ANY widget type to ANY zone
- Combo Profiles saved/loaded, swipe or button switching
- Gesture engine: swipe / multi-touch / long-press → any HID action
- Macro system: single key, chords (Ctrl+Alt+Del), recorded sequences, text typing
- Custom buttons with independent icon/label/color/size

### Module 8 — Polish & Delivery
- Unit + Compose UI tests green, leak check, zero-warning build
- CI: auto version increment, optional real signing via GitHub secrets, release notes
- README + cross-platform manual test checklist (Win/Mac/Linux/Android/TV)

---

## FIXED DECISIONS
- minSdk **29** (Android 10+) — confirmed by user. HID Device API needs 28+, so 29 is safe.
- Kotlin 1.9.22 + KSP (no kapt), AGP 8.2.2, Compose BOM 2024.02, Hilt 2.50, Room 2.6.1
- Package: `com.bluepilot.remote` (same appId so it upgrades over old installs)
- versionName starts at 3.0.0, versionCode 300

## PROGRESS LOG
- [x] Module 0 — Skeleton (built + unit-tested in CI-like sandbox, APK verified)
- [x] Module 1 — HID Core Engine (26 unit tests green, APK verified, 0 warnings)
- [x] Module 2 — Service + Connection Flow (34 unit tests green, APK verified, 0 warnings)
- [x] Module 3 — Data Layer (44 unit tests green, APK verified, 0 warnings)
- [x] Module 4 — Control Screens (58 unit tests green, APK verified, 0 warnings)
- [x] Module 5 — Widget Framework (75 unit tests green, APK verified, 0 warnings)
- [x] Module 6 — Customization Engine (89 unit tests green, APK verified, 0 warnings)
- [x] Module 7 — Combo Zones + Macros (103 unit tests green, APK verified, 0 warnings)
- [x] Module 8 — Polish & Delivery (103 tests green, debug + RELEASE APKs built & signature-verified, 0 code warnings, CI signing via secrets, README + manual test checklist)

## ✅ REBUILD COMPLETE — all 8 modules delivered

