# Wi-Fi TV remote preview (3.6.2-pre2)

AeroPad's existing Bluetooth HID controls remain intact. The Home tile,
**Wi-Fi TV Remote**, selects a process-wide TV target for TV boxes that implement
the UDP protocol observed in PhoneRemote 2.1.3. While selected, supported actions
from other AeroPad screens route to that TV IP; use **Disconnect TV target** to
return supported actions to Bluetooth. The target lasts for the current app
process. It is intended for compatible HiSilicon TV boxes; generic Android/Google
TVs are not guaranteed. The analyzed vendor APK and its libraries are not bundled.

## What it does

- Sends a local `POSTIP` broadcast and listens on UDP port 7899 for compatible
  TV replies; a TV IP can also be entered manually.
- Selects a TV using the `TVIP=<tv-ip>&<phone-brand> <phone-model>` handshake.
- Sends D-pad, OK, Back, Home, Menu, volume, mute, air-mouse movement/click, and
  search text commands in the `local-phone-ip&<command>` envelope. Manual edits and
  speech-recognition partials relay to the TV search field as they arrive.
- Routes supported plain key taps, text/search, pointer movement/left-click,
  scroll, media, and macro/shortcut/layout actions across the app while the TV
  target is active. TV selection is visible on Home and persists across screens.
- Modifier chords, held keys/buttons, right/middle mouse, true gamepad reports,
  gyro, touch-digitizer, and system power remain Bluetooth-only. Unsupported
  actions go to a connected Bluetooth host if one exists; otherwise AeroPad
  warns and does not pretend they reached the TV.
- Keeps TV traffic on the local network. No account or cloud service is involved.
  The phone's local IP and brand/model are visible to the selected TV.

## Security and limitations

This legacy UDP protocol has no authentication, encryption, or reliable command
acknowledgement. Discovery and input can be spoofed or observed by devices on
the same LAN. Use only on a trusted, non-guest home network; do not use it on
public Wi-Fi. A guest SSID, client isolation, VPN, firewall, or a competing app
bound to local UDP port 7899 may prevent discovery. This mode cannot wake a TV
or control its power unless the TV provides its own supported network feature.

The screen reports a selected IP, not a verified authenticated session. Real
hardware validation is still required for each TV firmware/model. The sandbox
has no TV on the same Wi-Fi, so local tests cover command encoding, IP checks,
route registration, unit tests, lint, and APK packaging—not TV interoperability.

## Local test

1. Put the Android phone and a PhoneRemote-compatible TV box on the same
   non-isolated Wi-Fi.
2. Launch AeroPad → **Wi-Fi TV Remote** → scan. If discovery is filtered, enter
   the TV's LAN IPv4 address manually.
3. Try D-pad/OK, Back/Home, volume/mute, air mouse, and search. Then open
   Keyboard, Mouse, Multimedia, a shortcut/macro, and a layout; supported actions
   should go to the selected TV IP.
4. Confirm the Gamepad screen explains that full HID/gyro still needs Bluetooth,
   and that disconnecting the TV target restores Bluetooth routing without
   changing pairings or device profiles.
