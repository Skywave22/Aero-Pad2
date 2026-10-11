# Privacy Policy — BluePilot Remote

**Effective date:** July 4, 2026

BluePilot Remote ("the app") is built privacy-first. This policy explains what the app does — and, more importantly, what it does not do.

## The short version
BluePilot Remote collects **no personal data**. Nothing you do in the app leaves your phone, except the Bluetooth input signals (keystrokes, mouse movement, gamepad input) sent directly to the computer or device **you** choose to connect to.

## Data collection
- **We collect nothing.** The app has no analytics, no advertising SDKs, no crash-reporting services, no user accounts and no server backend.
- The app does not use the internet. It requests no internet permission for its features.

## Bluetooth permissions — why they are needed
Android requires these permissions for the app to function as a Bluetooth keyboard/mouse/gamepad:

| Permission | Why |
|---|---|
| Bluetooth Connect | To establish the HID connection with the PC/TV you select |
| Bluetooth Scan | To list nearby devices when you search for a host (declared "neverForLocation" — not used to infer location) |
| Bluetooth Advertise | To make your phone visible to the PC during pairing |
| Location (Android 10–11 only) | Older Android versions require it for Bluetooth discovery; the app does not read or store your location |
| Notifications | To show the connection-status notification while the service keeps your session alive |
| Vibration | Optional touch feedback |

Bluetooth is used **exclusively** to transmit the input you actively perform (keys, pointer movement, media buttons, gamepad controls) to the device you paired with. No input is logged or stored beyond what you explicitly save as a macro on your own phone.

## Data stored on your device only
Your settings, themes, custom layouts, gamepad profiles and macros are stored locally in the app's private storage. Android backup of this data is disabled. Exported layout/gamepad files are created only where you choose to save them, and sharing them is entirely under your control.

## Children's privacy
The app collects no data from anyone, including children.

## Changes
If this policy ever changes (for example if optional crash reporting is added), the updated policy will be published at the same location with a new effective date, and any new data practice will be opt-in.

## Contact
Questions about this policy: open an issue on the project's GitHub repository.
