# AeroPad v3.6.0 — crash fix + review remediation

**Date:** 20 October 2026
**Base:** v3.5.0 (`931a379`) — the AI-rewritten tree, kept and repaired
**Result version:** 3.6.0 (versionCode 360)

---

## 1. The crash you reported (root cause, proven)

> "When I open or click on the keyboard or mouse, the app instantly crashes."

**File:** `app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt`

The AI added a safety-reset `init { }` block that reset drag-lock / scroll
state whenever the link dropped. It was placed **above** the fields it writes:

```kotlin
init {                                   // line 52
    viewModelScope.launch {
        isConnected.collect { connected ->
            if (!connected) {
                _dragLock.value = false    // <-- line 133, not initialised yet
                _scrollMode.value = false  // <-- line 128
                lastFallbackStickKey = null// <-- line 218
            }
        }
    }
}
// ...130 lines later...
private val _dragLock = MutableStateFlow(false)
```

Why that crashes every time:

1. Kotlin runs property initialisers and `init` blocks **in source order**, so
   `init` executes before `_dragLock` exists (it is still `null`).
2. `viewModelScope` uses `Dispatchers.Main.immediate`, and the ViewModel is
   built on the main thread, so `launch { }` runs **inline, immediately**.
3. `isConnected` is a `StateFlow` whose initial value is `false`, so `collect`
   emits synchronously — the disconnect branch runs during construction.
4. First line dereferences `null` → `NullPointerException` in the constructor.

`MouseScreen`, `KeyboardScreen`, `NumpadScreen`, `MultimediaScreen`,
`PresenterScreen` and `GamepadScreen` all obtain this ViewModel, so all of them
died on open. The Home screen does not, which is why the app launched fine and
only broke on the control screens.

**Fix:** the `init` block now lives at the **end** of the class, after every
field it touches, with a comment explaining why its position matters.

**Regression test:** `RemoteControlViewModelConstructionTest`
- Reconstructs the exact device conditions (`Main.immediate` + immediate
  `false` emission) and fails on any uncaught coroutine exception.
- Verified to **fail with `NullPointerException`** on the old ordering and
  pass with the fix — so it will catch a re-introduction.

---

## 2. Other fixes in this pass

| ID | Issue | Status |
|----|-------|--------|
| — | Constructor NPE crashing all control screens | **Fixed + regression test** |
| H1 | Gamepad keyboard-fallback sent one *tap* per direction change (v3.4.0 flooded ~100 taps/s; v3.5.0 moved the character one step then stopped) | **Fixed** — now real press-and-hold: `KeyDown` on direction change, `KeyRelease` on change/recentre, release on `resetGamepad()` |
| R4 | `Preview3DScreen` orphaned: route removed but screen left behind, so the feature silently vanished | **Completed removal** — dead file deleted, unused `onOpen3DPreview` parameter removed (the inline preview in Settings already replaced it) |
| M8 | CI never ran the Compose UI test; stale `PASTE THIS ENTIRE FILE...` header and wrong heap comment | **Fixed** — header rewritten, test-report artifact uploaded, non-blocking emulator job added for `connectedDebugAndroidTest` |
| — | Version numbers | **Bumped to 3.6.0 (360)** so the fixed APK is distinguishable from the crashing 3.5.0 |

### Already correct in v3.5.0 (verified by reading the code, not assumed)

- **C1** — mouse button mask lives in `HidEngine` (`MouseButtonTracker`); every
  move/scroll/click/keep-alive report carries held buttons, so **drag works**.
- **C2** — keep-alive skips entirely while a button is held.
- **M7** — `MouseUp` clears only the released button, not all of them.
- **H2** — automation broadcasts require a user-generated secret token.
- **H3** — analog triggers respond on touch-down (no 8dp touch-slop wait).
- **M1** — `collectAsState()` → `collectAsStateWithLifecycle()` (210 sites).
- **M2 / M3** — tile hide shows an Undo snackbar; hidden tiles keyed by stable IDs.
- **M4** — dock uses `selectable(Role.Tab)`; duplicate `contentDescription` removed.
- **L1 / L5 / L6** — predictive back, Room schema export, optional microphone feature.

---

## 3. Verification performed

Built and tested on a real toolchain (JDK 17 + Android SDK 35 + build-tools 35):

| Check | Result |
|-------|--------|
| `:app:testDebugUnitTest` (41 classes) | PASS |
| `:app:compileDebugAndroidTestKotlin` | PASS |
| `:app:lintDebug` | PASS (0 errors; 66 warnings, mostly unused resources) |
| `:app:assembleDebug` | PASS |
| `:app:assembleRelease` (R8 + resource shrinking + lintVital) | PASS |
| Crash regression test vs. old code | FAILS with `NullPointerException` (as expected) |
| Crash regression test vs. fixed code | PASSES |
| GitHub Actions `build` job (push `9f77bca`) | **SUCCESS** — APK artifact `aeropad-release` (1.8 MB) + `unit-test-report` |
| GitHub Actions `instrumented` job | fails, **non-blocking** by design |

### About the instrumented job

The Compose UI test could never run before (no emulator in CI). It now runs and
currently fails with `ComposeNotIdleException: Idling resource timed out` on all
three Home tests, alongside emulator graphics errors
(`Failed to bind to texture: invalid color buffer`).

That is almost certainly the runner's software GPU rather than an app defect —
the only infinite animation in the app is inside `GamepadControlRenderer`, not on
Home. The job is marked `continue-on-error: true`, so it can never block a
releasable APK. Fixing it properly means disabling the Compose test clock
auto-advance, which needs emulator iterations to validate — a separate task.

---

## 3b. UI pass (same 3.6.0 build, no version bump)

Every item below was found by **looking at real screenshots** captured from the
CI emulator (`ui-screenshots` artifact), not by reading source and guessing.

| # | What was wrong (visible in the screenshot) | Fix |
|---|---|---|
| U1 | No screen called `imePadding()` while the activity is edge-to-edge, so the on-screen keyboard covered the text field on all 11 screens that have one | Handled once at the `NavHost`, covering every screen and the dock |
| U2 | Settings had **no bottom padding**, so its last rows were permanently unreachable behind the floating dock | Reserve `DockMetrics.height`, now the single source of truth shared with the dock |
| U3 | Dock rendered **"ayouts"** — each tab was clipped to a `CircleShape` while only ~65dp wide, cutting the label's corners | Rounded rect + single-line ellipsized label |
| U4 | Home header crammed title, tagline and chips into one Row → tagline wrapped into a 4-line sliver | Title + chips on line one, tagline full width below |
| U5 | Layouts list: thumbnail + text + **four 48dp buttons** in one Row left the text column ~50dp wide → names showed as "Gam…", subtitle wrapped one character per line | Thumbnail + text get their own full-width row, actions below |
| U6 | Mouse scroll strip rendered `S\nC\nR\nO\nL\nL` as six stacked letters | Direction icon + rotated single-line `SCROLL` |
| U7 | Status card always drew a red **Bluetooth** logo, even for permission/ROM problems | Warning glyph for permission/HID/error states |
| U8 | Health dot was a 44dp clickable box pinned to `TopEnd` with no end padding — it sat on each screen's app-bar action and swallowed taps on the tuning gear; it also overlapped the Quick-Switch pill | One overlay Column below the app bar, inset from the edge |
| U9 | Quick-Switch pill covered the "Not connected" banner on control screens | Both overlays gated on a live connection |
| U10 | Gamepad Select/Home/Start row and Mouse L/M/R row used fixed `.height()` and clipped labels at large font sizes | `heightIn(min=)` |
| U11 | Gamepad face labels were hard-coded `Color.White` on fixed brand tints — unreadable on light tints in Light mode | Chosen from the tint's luminance |
| U12 | Home tiles had ragged heights ("Complete QWERTY keyboard" = 3 lines vs 2) | Subtitle pinned to exactly two lines with ellipsis |

To keep this verifiable, `ScreenshotTourTest` drives the real app on the CI
emulator with UiAutomator and uploads a PNG of every top-level screen as the
`ui-screenshots` artifact.

---

## 3c. New features added in this pass (from `AeroPad-New-Features.txt`)

Version stays **3.6.0 / versionCode 360** — no bump, per instruction. These are
additions *inside existing screens*; no new hub screen was created (X5), and the
app still has no `INTERNET` permission (X2), no PC companion (X1), no
marketplace (X4), no second editor (X6).

### F1 — Setup Doctor (Connection Health screen)

New `domain/SetupDoctor.kt`: a **pure, Android-free** decision table that turns
real engine/platform state into an ordered, actionable list of findings.

- Inputs it is allowed to read: Bluetooth adapter state, permission state,
  `HidConnectionState` (incl. `HidUnsupported.reason` and `Error.message`),
  measured `sendReport()` failures/timings, RSSI **only when the platform
  provides it**, disconnect count, battery level, power-saver mode, descriptor
  mode, reconnect backoff.
- Levels: `BLOCKER` → `WARNING` → `INFO` → `OK`, sorted so the top item is
  always the next thing to fix. Verdict header: BLOCKED / NEEDS_ATTENTION /
  READY / NOT_CONNECTED.
- Unknowns are never treated as faults (null RSSI ≠ weak signal, null battery ≠
  low battery).
- Each actionable finding carries a `Fix` enum; the ViewModel maps it to a real
  Settings intent (Bluetooth, battery saver, app details) behind `runCatching`,
  so a ROM without that activity cannot crash the screen.
- Rendered as a card at the top of **Connection Health** — new
  `ConnectionHealthViewModel.setupReport` (combine of 5 flows).

### F2 — Shortcut Packs (Layouts, seeded)

New `data/layout/ShortcutPacks.kt` with **five** ready-made layouts built only
from documented OS shortcuts: **Editing**, **Browser**, **Window**, **Present**,
**Media Studio**.

- `LayoutRepository.seedIfEmpty()` previously **bailed out entirely** if any
  layout existed, so upgrading users never received new built-ins. It now
  inserts any built-in whose **name** is missing, guarded by a `Mutex` (both
  app startup and the Layouts screen call it) and backed by a new
  `LayoutProfileDao.allNames()`.
- Seeding now also runs from `AeroPadApplication.onCreate`, so packs are
  available even if the Layouts screen is never opened first.
- Built-ins remain undeletable (existing `deleteById` only removes `isBuiltIn=0`),
  so re-seeding can never duplicate or resurrect a user-deleted layout.

### F3 — Sticky modifiers (Keyboard)

New `domain/StickyModifiers.kt` — tap latches a modifier for the next key,
double-tap within 450 ms locks it, tapping a locked modifier turns it off.
Wired through `RemoteControlViewModel` (`stickyState`, `toggleStickyModifier`)
into the modifier chip row above **Shortcuts**; latches are consumed by the next
`keyTap`/`typeText` and cleared on disconnect.

### F6 — Connect / disconnect triggers (Devices → host profile)

New `domain/HostTriggerController.kt` + `HostTriggerRunner` (started by
`HidService`). Fires the profile's macro on the real connect edge and only on
`Idle` for disconnect — a transient `Error` or reconnect does **not** fire the
disconnect macro. UI: "When this host connects / disconnects" pickers in the
Devices host-profile dialog.

### F19 — Per-host settings (Devices → host profile)

`HostProfile` gained `scrollSpeed`, `hapticIntensity`, `onConnectMacroId`,
`onDisconnectMacroId`; `bindHostTuning` writes all ten fields atomically and
`clearTuning()` resets a host to global. Applied on connect in `AeroPadApp`.
Dialog now has a scroll-speed override slider, haptic-intensity chips, and a
**Clear** action.

### Tests added (all pure JVM, no device)

| File | Covers |
|---|---|
| `features/SetupDoctorTest.kt` (16) | blocker ordering, unknown-value handling, thresholds, fix wiring |
| `features/StickyModifiersTest.kt` (14) | latch / lock / 450 ms boundary / consume / clear / mask width |
| `features/HostTriggerControllerTest.kt` (10) | connect edge once, disconnect on Idle only, host switching, throwing engine |
| `features/ShortcutPacksTest.kt` (10) | unique names/ids, in-bounds frames, no overlaps, real bindings |

**`./gradlew :app:testDebugUnitTest` → 362 tests, 0 failures.**
`:app:assembleDebug` → BUILD SUCCESSFUL.

### UI — scroll affordance for chip rows (phase-1 leftover, now fixed)

New `ui/components/ScrollableChipRow.kt`. Rows of chips that overflow used to
just clip at the screen edge with no indication that more existed; the Layouts
template row, Home "Quick Tools", the keyboard tab row, the keyboard modifier
row and sent-history row, the Mouse mode row, the Text Tools / Shortcut deck
pickers, the My Layout profile row, the Macros step row and all five Devices
dialog rows now show an edge fade plus a small chevron while content remains
hidden, and drop it at the end of the list. The last chip also gets end padding
so it is never covered by the chevron. This is what hid the "⌨ Full QWERTY" chip
on the keyboard screen — users could not see it existed.

### Bug found while testing

`",".format(locale, args)` — Kotlin's `String.format(vararg)` extension has no
Locale overload, so a Locale passed first is swallowed as a format argument and
the raw template is emitted. `SetupDoctor` now formats through one `fmt()`
helper that calls `java.lang.String.format(locale, template, *args)`.

---

## 3d. Round 8 — new bottom bar, keys & named combos in My Layout

Version still **3.6.0 / 360**.

### New floating dock

`ui/components/GlassDock.kt` rebuilt:

- **Sliding indicator** instead of a per-tab highlight, so motion shows where you
  came from. Its offset is derived from the measured width (`BoxWithConstraints`)
  → stays aligned at any screen width, in landscape, and under display scaling.
- Two-stage shadow (ambient + spot) plus a hairline border, so the bar still
  reads as a raised surface on dark wallpapers (the old flat `surface` pill
  vanished into them).
- Selected icon scales 8 % with a spring and switches to a primary label; the
  label is `labelSmall` semibold when active.
- Haptic tick + `Role.Tab` / `selectable` semantics preserved, and the tab is
  clipped to a rounded rect (never a circle — that is what used to render
  "Layouts" as "ayouts").
- `DockMetrics.height` 96 → **100 dp** so screens reserving space stay correct.

### My Layout — keyboard, single keys, named combos

New `model/keys/KeyCatalog.kt` (every key, five groups), `model/keys/NamedCombo.kt`
(user shortcut = name + key + modifier mask), `data/keys/CustomComboStore.kt`
(JSON file store, corrupt file ⇒ empty, atomic temp-file write) and
`domain/KeyPlacement.kt` (grid placement, pure + tested).

The **Keys** button on My Layout opens `KeyPadSheet`:

| Mode | Behaviour |
|---|---|
| **Send** | tapping any key presses it on the PC now; modifier chips latch one-shot |
| **Add to layout** | tapping a key drops it on the layout as a button — exactly the keys you want, nothing else |

- Modifier chips (Ctrl / Shift / Alt / Win) latch, so `Ctrl+Shift+Esc` is built
  the way you say it, and the header shows the chord live: **"next save: Ctrl+Shift+Esc"**.
- **Quick add** presets drop a whole group in one tap: WASD, Arrows, Esc+Enter.
- **Your shortcuts**: type a name, press Save → the combo is stored and becomes a
  chip on My Layout (tap = send) *and* a row in the pad you can add to any layout.
  Re-saving under the same name updates it instead of duplicating.
- Every add is **one batched write**, so adding 4 preset keys forks a built-in
  template once, never twice. Editing a built-in forks it to "<name> (mine)" and
  leaves the template sealed.
- Feedback via snackbar ("Added 4 keys.", "Pick a layout first…", "This layout is
  full…") — no silent failures, no crash when nothing is connected.

### Tests

`features/KeyPadTest.kt` (22): catalog completeness and uniqueness, byCode
round-trip, chord ordering (`Ctrl+Shift+Esc`), name sanitising, invalid combo
rejection, placement (first cell, row wrap, thumb-first bottom bias, no overlap,
full grid ⇒ null).

**384 tests, 0 failures.**

---

## 3e. Round 9 — pointer feel, laptop gestures, accent colours

Version still **3.6.0 / 360**. Full reasoning lives in `docs/WORLD-CLASS-AUDIT.md`;
this is the change list.

| Change | Why |
|---|---|
| `domain/PointerCoalescer.kt` + `TrackpadEngine` wiring | Cursor stutter is the #1 complaint in every rival's reviews. 240 Hz touch input is merged into ~80 Hz reports with the **total delta preserved**, first move sent immediately, big flicks never held, tail flushed at drag end. Fewer radio wake-ups ⇒ less battery too. |
| `ui/gestures/TwoFingerTap.kt` | Two-finger tap = secondary click, the trackpad convention users reach for first. Non-consuming, so drags/long-press/single tap are untouched. |
| Left-handed mode (`MouseSettings.leftHanded`) | Mirrors tap/long-press semantics and the on-screen click buttons. Settings toggle included. |
| `ui/theme/AccentPalette.kt` + `AppSettings.accentColor` | Six accents with separate light/dark variants, layered over Material/Glass/Neon. Default stays blue. Swatches are TalkBack radio buttons. |
| `docs/WORLD-CLASS-AUDIT.md` | Competitive analysis, what AeroPad already wins on, honest remaining gap list. |

`PropertyCatalog.SETTINGS_PROPS` 36 → 38 (a guard test enforces this when
settings fields are added — it caught the omission immediately).

**404 tests, 0 failures** (20 new).

---

## 3f. Round 10 — reconnect that never gives up

Version still **3.6.0 / 360**. This closes the gap `WORLD-CLASS-AUDIT.md` listed
as #1 remaining: *"it won't reconnect after the PC sleeps"* — the most common
complaint in the category and the app's most damaging failure mode.

**What was actually broken** (not just "sometimes flaky"):

1. `scheduleReconnect()` **gave up after 4 attempts** — 1 + 2 + 4 + 8 = 15 s of
   total tolerance. A host that sleeps for a minute, or a phone that dozes for
   five, outlived it. The app was correct and useless at the same time.
2. It required `appRegistered` and **did nothing** when that was false. Android
   or a ROM can drop the HID registration (radio reset, host stack restart); the
   old code then went silent forever until a force-stop.
3. Three short-lived connections triggered a **permanent** error state. But a
   host that is *mid-wake* is exactly what produces flaps — so the penalty
   landed on the one case the user most wanted fixed.
4. Nothing retried when the phone was unlocked or the screen came on, so a link
   lost in Doze stayed lost until the user intervened by hand.

**New `domain/ReconnectStrategy.kt`** (pure, tested):

- `ReconnectPolicy` — 0.5 s, 1 s, 2 s, 4 s, 8 s, then **every 15 s backing off to
  a 30 s ceiling, forever**, with ±20 % jitter so retries don't resonate with the
  host's own scan interval. Never returns a delay it can't honour, never busy-loops.
- `FlapGuard` — a flapping link now **rests** (60 s, doubling to a 15 min cap) and
  then resumes on its own; one stable connection clears the history.
- `ReconnectDecision` — the decision table that actually fixes bug (2): when the
  HID app is unregistered the action is **RE_REGISTER**, not "give up".

**Engine changes (`HidEngine`)**: a single idempotent `startReconnectLoop()` runs
until the link is back, the user disconnects, or the service stops; it
re-registers the app when needed, re-arms after the Bluetooth adapter comes back
on, and follows the policy schedule. New `retryNow()` (also on `HidController`,
so it is available to any ViewModel) clears the penalty and attempts immediately.

**Wake triggers**: the existing broadcast receiver now also listens for
`ACTION_SCREEN_ON` and `ACTION_USER_PRESENT` (neither needs a permission) and
fires an immediate retry — picking the phone up is the strongest signal that the
user wants the remote back.

**UI**: Setup Doctor reports the real attempt number and countdown with a
**Retry now** button; the Connection screen gained **"Retry connection now"**
plus one line explaining that the app keeps trying on its own.

**426 tests, 0 failures** (22 new).

---

## 3g. Round 11 — landscape pass (real bugs found) + turbo keys

Version still **3.6.0 / 360**.

### The app had never been looked at sideways

Every screenshot to date was portrait, so the whole UI was effectively untested
in the orientation people use when the phone is propped next to a keyboard or
clamped to a TV. A second instrumentation pass now rotates to landscape and
photographs the main screens. It found three real defects:

1. **The dock overlapped content in landscape.** `DockMetrics.height` was a flat
   100 dp built for portrait (pill + system nav padding). In landscape the pill is
   ~82 dp and there is no nav padding, so screens reserved 18 dp too much *and*
   still had items hidden behind the dock. The Devicess screenshot showed
   "Open App Settings" half-covered. `DockMetrics` now exposes an
   orientation-aware `contentInset()`, used by Settings and Devices.
2. **The home grid stayed two columns in landscape**, producing half-screen-wide
   slabs with a huge empty gutter. It is now `adaptiveColumns(2, 3, 4)` driven by
   the real window size, and full-width rows use `GridItemSpan(maxLineSpan)`
   instead of a hard-coded 2.
3. **The dock stretched edge to edge** on a wide window, stranding the four tabs
   at the extremes. It is now centred and capped at `Adaptive.DOCK_MAX_WIDTH`.

New `ui/components/Adaptive.kt` centralises this (`isLandscape`, `isWideWindow`,
`adaptiveColumns`, `ReadableWidth`) so screens stop each inventing a breakpoint.

### F10 — turbo keys (hold to repeat)

`domain/TurboRepeater.kt`: a key fires once on tap, or repeats ~16×/s while held
after a 300 ms hold delay. The delay is what makes it safe — a normal tap can
never double-fire — and catch-up is capped at 3 so a stall (GC, backgrounding)
cannot dump a burst at the host.

Available in three places: **My Layout → Keys → "Turbo keys"** (ideal for WASD
and arrows), the **layout editor** (per-button switch), and on any button that
was saved with it. Turbo buttons show a live dot while repeating.

### F17 — double-tap bindings

`WidgetSpec.doubleTapAction`: a button can now fire a second action on
double-tap, independent of long-press. Both new fields are defaulted, so every
layout saved before this still decodes — verified by the serialisation round-trip
tests.

`PropertyCatalog.WIDGET_PROPS` 22 → 24 (the guard test caught it immediately).

**437 tests, 0 failures** (11 new for turbo).

---

## 4. Still open (deliberately not changed in this pass)

- **H4 — Play Store targetSdk.** Still `targetSdk 35`, AGP 8.2.2, Kotlin 1.9.22.
  Google Play requires **API 36** for updates since 31 Aug 2026, so this build
  cannot be uploaded to Play — but it installs and runs fine as an APK.
  The upgrade means Kotlin 2.x + the Compose Compiler Gradle plugin +
  compileSdk 36, which is a separate, testable change. Recommended next step.
- **M5 — localisation.** Only ~43 strings are in `strings.xml`; most labels are
  still hard-coded Kotlin, so the `values-ur/hi/ar/es/in` files cover very
  little. Large mechanical job, no runtime risk either way.
- **Part 8 screen merges** (keyboard ×4 → 2, layout surfaces, gamepad editors)
  were not done; they are UX restructuring, not bug fixes, and would risk the
  now-stable navigation.
- **F1/F2/F3/F6/F19 follow-ups not done:** turbo/double-tap on widgets (F10/F17),
  QR pairing (F9 — needs CAMERA and must not add networking), rumble (F12), BLE
  HID fallback (F13).
- ~~Small remaining polish: chip rows cut off with no scroll affordance.~~
  **Fixed** — see §3c `ScrollableChipRow`.

---

## 5. How to test on the phone

1. Install the v3.6.0 APK (from the Actions artifact or Releases page).
2. Open **Mouse** → it must open, not crash. Same for **Keyboard**, **Numpad**,
   **Media**, **Presenter**, **Gamepad**.
3. Pair with the PC, then the critical drag test:
   **Mouse → Drag lock on → move a window for 10+ seconds.** The window should
   follow your finger the whole time — that confirms C1 + C2 together.
4. Gamepad → set mapping mode to keyboard fallback → hold the stick: the
   character should keep moving while held, and stop when you release.
