# AeroPad audit and stabilization

**Baseline:** `24b61c1` (v3.6.1), branch `arena/c4c37095-aero-pad2`. The worktree was clean at the start. This document separates source findings, changes on this branch, and checks still needed for release.

## Architecture and feature inventory

- One Android application module, Kotlin 1.9.22, Compose Material 3, Navigation Compose, Hilt/KSP, coroutines and StateFlow. Room stores layouts and macros; DataStore stores tuning and preferences; private JSON files back several smaller stores. Build: Gradle wrapper 8.5, AGP 8.2.2, JDK 17, minimum SDK 29, compile/target SDK 35.
- `MainActivity` applies window preferences and hosts `AeroPadApp`. UI gestures call ViewModels and `HidController` use cases. The singleton `HidEngine` registers Android's classic Bluetooth HID Device profile and encodes reports using `HidDescriptors`, `HidReportBuilder`, and `ReportPool`. `HidService` retains the link and exposes status/actions. The wire descriptors were not replaced.
- The Android remote provides standard Bluetooth HID keys for compatible phones/TVs. There is no separate Wi-Fi TV protocol, Wi-Fi route, or Rust/JNI integration in this checkout.

| Entry point | Routes and secondary flows |
| --- | --- |
| Dashboard and setup | Home → permissions, connection, and paired/nearby devices. Onboarding includes Windows, macOS, Android TV, and Linux/Deck guides. Connection provides Bluetooth enable, discoverability, cancel/disconnect, and retry. Devices includes host favorites, layout/tuning bindings, discovery, and connection status. |
| Pointer and typing | Mouse/trackpad → tuning and Air Mouse. Keyboard → Full Board; Numpad also has a dashboard shortcut. PC Combo combines inputs. Text & Clips covers text relay, snippets and clipboard tools; speech and IME are subflows rather than routes. |
| Other controls | Gamepad → Builder and in-screen profiles; Multimedia → playback/volume/brightness; Presenter → slide and presentation keys; Android → D-pad/system/media and the optional Touch Screen digitizer; Decks → shortcut sets. |
| Create and customize | Layouts → custom layout player, import/export, and `layout_editor/{profileId}`; My Layout → custom keys and key picker; Macros → record/edit, loop, test and stop; Gamepad Builder → list, play, edit/preview, import/export and versioning. |
| System | Settings → theme, input tuning, behavior, release notes, tutorial replay, and connection health; Help → FAQs; Connection Health → link/report metrics, retry and diagnostics. App shortcuts, the Quick Settings tile, and optional token-gated automation are non-graph entry points. |

The graph has 24 static destinations and one parameterized editor. Dialogs, menus and modes are listed by role above; the inventory is not an assertion that each was exercised on-device.

## Prioritized findings and changes

| Priority | Evidence and impact | Change / remaining risk |
| --- | --- | --- |
| P0 release gate | Wrapper was tracked without its executable bit. Android build and release verification cannot be assumed from source review. | Restored `gradlew` executable. Build attempt still stops before Gradle starts because the sandbox cannot download Gradle 8.5. Compile, lint and device-matrix runs remain required. Keep release signing verification enabled; no signing configuration or application ID changed. |
| P1 input ownership | Suspending `TypeText`/tap sequences used the current host when they resumed; lifecycle and connection callbacks could race report state. Mouse movement could also cut a transient click short. | Serialized HID mutations and report execution on the HID executor. Input sessions cancel old queued/suspended actions. Same-channel timed pulses are ordered; mouse clicks maintain a transient mask separate from held buttons. Manual pause/resize, destination change, link teardown and descriptor change cancel queued input and attempt neutral reports while the link is usable. Unexpected physical link loss cannot deliver a final release to the disconnected host. |
| P1 Bluetooth lifecycle | Registration was requested immediately after unregistering. Stale host/proxy callbacks could clear state; a new host could be requested while another was still active. The foreground service was started only on the Connection route. | Tracked callback ownership, added bounded setup/connect waits, serialized host switching, handled Bluetooth off/on broadcasts, and reconciled link history/status on teardown. The foreground service now starts from activity resume with permission and stops the engine on teardown. Retained the classic descriptors, opt-in extended reports and reconnect policy. Legacy GET_REPORT gamepad replies now use the legacy length. OEM behavior still requires hardware testing. |
| P1 visible controls | Floating dock and connection overlays consumed taps and covered content; hard-coded padding double-counted space on some hubs. Media/Presenter fixed rows fell outside short windows. Numpad could shrink below usable touch sizes. | Measured dock outside content, consumed system bar/cutout/IME insets at the shell, and moved health/quick-switch into reserved space (side rail in landscape). Adaptive media/presenter panes, conditional numpad scrolling and capped reading widths. No change to device orientation policy. |
| P1 gesture fidelity | The Touch Screen digitizer used a drag detector, so ordinary taps did not produce a contact. Motion streams and latched controls could outlive the active screen. | Digitizer sends down on first contact, follows one pointer and releases in `finally`. Air Mouse, gamepad and gamepad-builder motion/hold state is stopped or cleared on lifecycle interruption. Trackpad drag lock resets on exit, pause and window resize. |
| P1 import and discovery | Layout and gamepad imports read arbitrary document streams on the UI thread. Discovery showed activity even after a rejected/finished scan. | Shared 1 MiB stream limit and off-main import reads with feedback. Discovery stops on Android's completion event, timeout, explicit Stop or leaving Devices; failures have recovery text. |
| P2 routes and state | A shortcut could replace the Home root; malformed editor paths were forwarded to Navigation; system Back skipped player/editor parent screens. Undoing a dashboard hide could overwrite a different tile's visibility change. | Home is the root for shortcuts, routes are canonicalized, Devices/Connection avoid adding reciprocal entries, and Back exits the active layout/player/editor mode. Tile IDs/legacy titles remain recognized and individual hide/restore updates are transactional. |
| P2 design and accessibility | Light/dark styling diverged across small controls; fixed dashboard columns clipped labels, onboarding intercepted taps via an invisible click target, and offline controls had no direct recovery action. | Lavender Material default with existing accents, Light/Dark/System, Glass, and Neon preserved. Shared control buttons, semantic headings and actions, adaptive dashboard spacing, focus-owning onboarding dialog, and a contextual Connect action. A saved Blue preference remains Blue. |

### Shared design system

Use `LocalAppTheme` and Material 3 colors for themed foregrounds, not fixed accent colors. Material surfaces use neutral light or OLED-dark foundations, violet primary, subdued teal secondary, and text alongside connection colors. Use the shared Material shape scale (10 dp small, 20 dp medium at the default radius), 8–16 dp internal spacing, and 48 dp touch targets where controller geometry allows. Standard controls use outlined/tonal surfaces; explicitly chosen Glass/Neon finishes still use depth. `KeyCard` retains one-line labels on dense keyboards and allows selected wider actions to wrap. App-level navigation uses a short fade (none with Reduce motion) and reserves space instead of laying hit targets over control surfaces. Connected and disconnected user actions must be labeled independently of color.

### Open findings

- Long macro playback produces its own future actions, independent of queued HID report sequences. Test a running macro across an explicit host change and background/foreground; macro-wide session ownership is still a separate hardening item.
- OEMs and hosts differ in HID registration, report caching after descriptor changes, notification/background restrictions and reconnect policy. Use real hosts for timing and stuck-input checks. No programmatic report can guarantee host release after the radio link is already lost.
- Dedicated editor/canvas layouts, full-keyboard row density, haptics/contrast in every theme, RTL and 200% font scaling still need device-level visual and accessibility verification. No screenshots or screen-reader runs were possible here. Settings labels are not fully localized; the existing resource translations remain in place.
- Per-host tuning currently writes to shared settings on connect; verify override/reset expectations with two real hosts. This pass did not change persistence schemas or per-host tuning semantics.
- No signing files were found in the current tree. The repository's signing documentation refers to previously committed signing material; confirm the key/upgrade policy before a public release. An installable debug artifact is not a substitute for a verified release signature.

## Verification

| Check | Observed result |
| --- | --- |
| `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Initially blocked by wrapper mode and missing Java. After fixing mode and obtaining a temporary Java 17 runtime, still failed at `services.gradle.org` with `SSLHandshakeException` downloading Gradle 8.5. None of the three tasks ran. Android SDK/ADB/emulator are not installed here. |
| Kotlin compiler 1.9.22 PSI syntax check | Parsed 219 Kotlin source files with zero syntax errors. This is not Android compilation or Compose type checking. |
| Standalone Kotlin smoke harness | Compiled the real HID engine, codec, route guard and import reader with temporary Android API stand-ins. Passed ordered timed taps, old input cancellation across a host switch, stale-host disconnect filtering, simulated descriptor re-registration, neutral touch release, legacy report sizing, mouse mask, malformed routes, and UTF-8/size-limit reads. This was not an Android integration or hardware test. |
| Static cross-checks | Graph matches the guard (24 static routes plus editor). All three Maestro files parse as YAML; the scenarios were not run. Selected base color pairings passed a 4.5:1 contrast calculation (white/violet 7.10:1, dark ink/light violet 10.47:1, muted text on variants over 7:1). `git diff --check` and `bash -n` for the wrapper and repository shell utilities passed. |
| Regression coverage added | Unit cases for input-session cancellation, neutral report length, click masks, malformed routes and import size/encoding; updated discovery and palette assertions. Compose/Maestro cover reachable dashboard items and pairing navigation. Their normal Gradle/instrumentation tasks are still unrun. |

### Release test matrix (pending)

1. JDK 17 and Android SDK 35: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. With a device/emulator: `./gradlew :app:connectedDebugAndroidTest`; install the debug APK and run `maestro test .maestro/` in light and dark. Run `tools/apk-audit.sh` against the built APK.
2. Fresh install; Bluetooth off/on; Nearby Devices denied then granted, denial/settings recovery; location-based discovery on API 29–30; optional notification permission denied on API 33+.
3. Pair from Windows/macOS/Linux and a compatible Android TV or phone where available. Verify scan/cancel, timeout/retry, manual disconnect, unexpected drop, Bluetooth toggle, host A→B switching, and descriptor mode change with repair/re-pair instructions if needed.
4. On a live host, hold a mouse button, latched drag, key, gamepad button or touch contact. Then navigate, resize or rotate, background, lock/unlock and disconnect mid-gesture. Verify release and verify no old input reaches a new host. Repeat for long text, turbo and macro playback. Test keep-alive, battery saver and service notification actions.
5. Walk all routes above, including shortcut→Back→Home, editor/player Back, save/restore/restart, import/export and tuning. Inspect a 320 dp phone, typical phone, landscape, split-screen and tablet with 100–200% font sizes, dark/light and TalkBack. Check gesture navigation and keyboard insets with Text & Clips, keyboard relay and Settings search open.

## Rust core

The supplied brief describes an existing Rust core, but this shallow checkout has no `Cargo.toml`, `.rs` files, JNI/NDK/native-library packaging, or native bridge calls. The public repository language inventory also lists Kotlin and Shell. The implemented input path here is the Kotlin/Android HID stack. No Rust module or bridge was removed, bypassed or added; `cargo fmt --check`, `cargo test`, `cargo clippy`, and Android native integration verification were not applicable to the contents available. If the intended Rust code lives in another revision or repository, it must be supplied to audit and test it.
