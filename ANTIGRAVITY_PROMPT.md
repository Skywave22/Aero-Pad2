# PROMPT FOR ANTIGRAVITY IDE — Copy everything below this line into the agent

---

You are working on **BluePilot Remote v3.1** — a complete, WORKING Android app (Kotlin, 100% Jetpack Compose, Material 3, Hilt, Room, DataStore, minSdk 29, targetSdk 34). It compiles clean with 135 passing unit tests. Do NOT break any functionality — this task is a **pure visual/UI implementation pass**.

## THE TASK

The folder `design-mockups/` in the project root contains 49 high-fidelity design mockups (JPG). Your job: make the app's real Compose UI **visually match these mockups as closely as Android allows** — same layouts, same glass materials, same colors, same icon treatments, same spacing and composition. Study every mockup image carefully before coding. The current app implements all features but looks far flatter than the designs.

## MOCKUP FILE MAP — which images define which screens

Naming: each SET is one complete design theme. Files show DARK and LIGHT variants side by side in most images.

### SET "v2-" (PRIMARY TARGET — implement this one first and most faithfully)
Liquid Glass × Material 3 hybrid. These reflect the app's actual current features:
- `v2-01-home-dark-light.jpg` → HomeScreen: glass tile grid with COLORFUL GEL 3D icons (glossy gummy squares: blue mouse, purple keyboard, orange numpad, cyan multimedia, gold presenter, pink gamepad, teal layouts, green macros, violet themes, indigo pad-builder), frosted status capsule with green dot + Bluetooth glyph, floating pill-shaped glass bottom dock, version chip "3.1.0"
- `v2-02-theme-gallery-dark-light.jpg` → ThemeGalleryScreen: 2-column cards, each containing a realistic mini phone-screen preview of its theme, active card has glowing border + check badge + "Applied"
- `v2-03-padbuilder-dark-light.jpg` → GamepadBuilderScreen editor: frosted toolbar, add-chips row, glass joystick/D-pad/gel ABXY buttons on canvas, selected control with glowing outline + corner handle, Control Properties bottom panel
- `v2-04-recorder-gestures-dark-light.jpg` → MacrosScreen recorder bar (red-tinted glass "● REC — N steps") and gesture-zone widget with 4 glowing direction arrows
- `v2-05-mouse-dark-light.jpg` → MouseScreen: huge frosted trackpad with specular streak, slim glass scroll rail, 3 glass pill buttons, pressed state at 94% scale
- `v2-06-settings-dark-light.jpg` → SettingsScreen: frosted group cards, glass segmented chips, glowing gel switch thumbs, "Open theme gallery" button, Light/Medium/Strong vibration chips
- `v2-07-transitions-dark-light.jpg` → navigation transition reference (260ms slide+fade — already implemented, keep it)
- `v2-08-design-system.jpg` → the design-token reference sheet (colors, radii, glass tiers, typography)

### SET "glass-" — "Liquid Glass" + "Frost Glass" theme definitions
8 files: home, mouse, keyboard, editor, gamepad, pairing+media, settings+macros, design system. Frosted panels over aurora orbs, luminous edge highlights.

### SET "hi-" — "Hawaii Night" + "Hawaii Day" themes
8 files. Lagoon teal/coral gels, water-caustic feel, sea-glass pebble buttons, tiki-lantern warm glow (dark) / sun-washed aqua + sand (light).

### SET "hud-" — "Cockpit HUD" + "Day Flight" themes
8 files. Carbon black + neon green HUD readouts / brushed aluminum + navy-amber. Monospace fonts, instrument bezels, corner-bracket selection states, radar-style pairing screen, cockpit-switch keycaps.

### SET "lgm-" — "You Glass Dark/Light" themes
8 files. Material-You tonal palettes + glass surfaces, mesh gradient backgrounds, M3 pill nav bar.

### SET "01"–"08" — "Pilot Glow" theme
8 files. Dark navy + blue neon glow accents, flat-modern Material.

### `app-icon-concept.jpg` → launcher icon reference

## EXISTING ARCHITECTURE YOU MUST USE (do not re-architect)

- **Theme engine**: `app/src/main/java/com/bluepilot/remote/ui/theme/`
  - `AppThemeSpec.kt` — theme data model: colors, backgroundOrbs, cornerRadius, surfaceAlpha, edgeGlow, glowColor, monoFont
  - `BuiltInThemes.kt` — 13 theme specs already defined, matching the mockup sets (pilot_dark, pilot_glow, liquid_glass, liquid_glass_light, glass_you_dark, glass_you_light, hawaii_night, hawaii_day, cockpit_hud, day_flight, dark_neon, oled_black, minimal_light)
  - `ThemeEngine.kt` — LocalAppTheme CompositionLocal + BluePilotAppTheme M3 bridge + ThemedBackground orb painter
  - EXTEND AppThemeSpec with new style tokens as needed (gel icon palettes, specular highlights, dock style) — then consume them in shared components.
- **Screens** in `ui/screens/`: home, themes, mouse, keyboard, numpad, multimedia, presenter, gamepad, gamepadbuilder, layouts, editor, macros, settings, connection, devices, permission, help.
- **Shared components** in `ui/components/` (KeyCard has press-scale + haptic intensity — KEEP those behaviors).
- Widget renderer: `ui/widgets/WidgetRenderer.kt`; gamepad renderer: `ui/gamepad/GamepadControlRenderer.kt`.
- DO NOT TOUCH: `hid/`, `bluetooth/`, `service/`, `data/`, `domain/` (ViewModels may gain UI-only state if needed).

## SPECIFIC VISUAL TECHNIQUES REQUIRED

1. **Glass panels**: translucent surface + `Brush.verticalGradient` white sheen overlay (top ~12% alpha → 0%) + 1dp gradient border (brighter top edge) + on Android 12+ optionally `Modifier.blur()` haze layers behind cards. Build ONE reusable `GlassPanel`/`GlassCard` composable and use it everywhere.
2. **Gel icons** (the single most visible gap): rounded-square badge with vertical two-tone gradient of the tile's hue, inner top highlight (small white-alpha rounded rect near top), thin white-alpha border, white icon, soft drop shadow of the same hue. Build a `GelIcon(color, icon)` composable.
3. **Background orbs**: already implemented in ThemedBackground — increase richness to match mockups (2–4 orbs; subtle drift animation with `rememberInfiniteTransition` is a bonus).
4. **Floating dock**: give Home the mockups' floating pill-shaped glass bottom dock (Home / Devices / Settings) if feasible without breaking navigation.
5. **HUD theme extras**: when active theme is `cockpit_hud`/`day_flight`, selection outlines become corner brackets, labels uppercase monospace, status pill styled as an avionics strip (theme-conditional rendering via `LocalAppTheme.current`).
6. **Per-theme fidelity**: switching themes in the gallery must transform the app between the SETS — Liquid Glass look ↔ Hawaii look ↔ HUD look, driven by spec tokens. Never hardcode colors in screens; always read from the theme.

## HARD CONSTRAINTS

- All 135 unit tests must keep passing: `./gradlew :app:testDebugUnitTest`
- Zero compile errors and zero new Kotlin warnings: `./gradlew :app:assembleDebug`
- Do not modify: HID descriptors/engine, Bluetooth code, Room schema, macro engine, gesture logic, ProGuard rules, CI workflow.
- Keep all existing behaviors: press-scale animation, haptic intensity, 260ms screen transitions, gesture zones, macro recorder, edit modes, import/export.
- minSdk 29: any blur usage gated on `Build.VERSION.SDK_INT >= 31` with graceful alpha-only fallback.
- Performance: no per-frame allocations; keep 60fps; prefer draw-phase work (Canvas) over recomposition.

## WORKFLOW

1. Open and STUDY all `design-mockups/v2-*.jpg` files first.
2. Build the shared glass component kit (`GlassPanel`, `GelIcon`, floating dock).
3. Rework HomeScreen to match `v2-01` pixel-closely (verify BOTH dark and light with the Liquid Glass and Frost Glass themes).
4. Rework ThemeGalleryScreen to match `v2-02`.
5. Then screen by screen: mouse (v2-05), settings (v2-06), pad builder (v2-03), macros/gestures (v2-04), then remaining screens using their set-specific mockups.
6. Add HUD-conditional styling from `hud-*` files.
7. After EVERY screen: compile, run tests, and visually compare against the mockup side by side in preview/emulator. Iterate until the match is convincing.
8. Final pass: `./gradlew :app:testDebugUnitTest :app:assembleDebug` — all green.

Deliverable: the same app, same features, but looking like the mockups.

---
END OF PROMPT
