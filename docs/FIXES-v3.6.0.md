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
- **Small remaining polish seen in the screenshots:** the Layouts template chip
  row is horizontally scrollable but gives no affordance that more chips exist
  off-screen (the third chip is cut), and the same is true of the home "Quick
  Tools" row.

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
