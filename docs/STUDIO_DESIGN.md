# AeroPad Studio implementation

The approved portrait mockup is the reference for the Studio design. Its five object illustrations are separate, transparent WebP resources cropped from the supplied concept, not images of UI. The wordmark, controls, state, grid and typography are rendered natively in Compose. Total artwork is about 158 KB, packaged offline. The launcher identity and application ID are unchanged.

## Design across the app

- **Home:** Studio wordmark, live connection badge, blueprint pairing panel and lime action, four illustrated shortcuts, customization and Studio tools. A real permission, adapter, connection or recovery state determines the copy and destination of each pairing action.
- **Controls:** The new tab and Home's **View all** use the same control catalog. Pointer, keyboard, gamepad, media, presentation, Android, touch, shortcut and builder routes remain available even when their Home shortcuts are hidden. Devices is reachable from Home, the Controls toolbar, and Bluetooth setup.
- **Shared chrome:** Studio typography, smaller rounded surfaces, ink and plum neutrals, outlines, and consolidated top bars style control, setup, settings, editor, library and help screens. Live Trackpad and Touch Screen use low-contrast drafting grids rather than large decorative artwork. Pairing and Devices use the same blueprint/status language as the Home hero.
- **Responsive behavior:** Home uses two cards per row on ordinary phones, four on wide windows and one when width or font scale would make labels too cramped. Narrow or high-font-scale layouts omit decorative hero art. Functional controls are not moved behind artwork. The bar remains in the layout, and its compact landscape variant uses accessible icon-only tabs.
- **Preservation:** Bluetooth implementation, HID descriptors, launcher shortcuts, themes and accent overrides are unchanged by this visual pass. Home customization still recognizes stored legacy labels such as `Mouse` and `Multimedia` and their route IDs. Functional layouts and editor gesture coordinates retain their existing contracts.

## Verification

- Parsed 226 Kotlin sources without syntax errors and validated the Android resource XML and Maestro YAML.
- A standalone Kotlin check passed for all HID-to-dashboard state mappings and the guarded Controls start route. All 21 catalog entries match registered routes; original destinations remain in the graph.
- Decoded and visually checked the five transparent assets; checked at least 4.5:1 contrast on the declared card and pairing foreground/background pairs. This is not a rendered Android screenshot or a visual device test.

The Android build, Gradle unit suite and lint need a JDK 17/SDK 35 environment with Gradle downloads available: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. This environment failed with a TLS handshake error before Gradle could run. The updated Compose and Maestro flows should be run on a device or emulator, including narrow portrait, landscape, dark mode, and large font sizing. See [APP_AUDIT.md](APP_AUDIT.md) for the earlier stabilization report and real-device Bluetooth matrix.
