# Privacy Policy — AeroPad Remote

**Effective date:** October 11, 2026

AeroPad Remote ("the app") is built privacy-first. This policy explains what the app does — and, more importantly, what it does not do.

## The short version
AeroPad Remote collects **no personal data**. Nothing you do in the app goes to an AeroPad server. Input leaves the phone only when you actively use Bluetooth HID with a paired host or select a compatible TV and send supported local UDP commands over your Wi-Fi.

## Data collection
- **We collect nothing.** The app has no analytics, no advertising SDKs, no crash-reporting services, no user accounts and no server backend.
- Core keyboard, mouse and gamepad features use Bluetooth HID. When a compatible Wi-Fi TV is selected, supported keyboard, live text/search, pointer, media, macro, shortcut, and layout actions route as local UDP commands on port 7899 to that TV. The app does not use a cloud service or send these commands to an AeroPad server.

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

Bluetooth transmits input to the Bluetooth host you pair with. With a Wi-Fi TV target selected, supported commands are instead broadcast on the local network for that TV; the protocol has no authentication or encryption, so use it only on a trusted home network. No input is logged or stored beyond what you explicitly save as a macro on your own phone.

## Data stored on your device only
Your settings, appearance choice, custom layouts, gamepad profiles and macros are stored locally in the app's private storage. Android backup of this data is disabled. When you use the TV remote, the app sends your phone's local Wi-Fi IP address and device brand/model label to the selected compatible TV as part of its legacy UDP protocol. No account, advertising, analytics or cloud telemetry is used. Exported layout/gamepad files are created only where you choose to save them, and sharing them is entirely under your control.

## Children's privacy
The app collects no data from anyone, including children.

## Changes
If this policy ever changes (for example if optional crash reporting is added), the updated policy will be published at the same location with a new effective date, and any new data practice will be opt-in.

## Contact
Questions about this policy: open an issue on the project's GitHub repository.
