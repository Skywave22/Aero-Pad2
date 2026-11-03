# Wi-Fi TV remote preview (3.6.2-pre1)

AeroPad's existing Bluetooth HID controls remain intact. The new Home tile,
**Wi-Fi TV Remote**, is a separate client for TV boxes that implement the UDP
protocol observed in PhoneRemote 2.1.3. It is intended for compatible HiSilicon
TV boxes; Android TV, Google TV, and other brands are not guaranteed to work.
The analysed vendor APK itself and its native libraries are not included.

## What it does

- Sends a local `POSTIP` broadcast and listens on UDP port 7899 for compatible
  TV replies; a TV IP can also be entered manually.
- Selects a TV using the `TVIP=<tv-ip>&<phone-brand> <phone-model>` handshake.
- Sends D-pad, OK, Back, Home, Menu, volume, mute, air-mouse movement/click, and
  search text commands in the `local-phone-ip&<command>` envelope.
- Keeps all TV traffic on the local network. No account or cloud service is
  involved. The local IP and phone brand/model are visible to the selected TV.

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
3. Try D-pad/OK, Back/Home, volume/mute, air mouse, and search; continue to use
   the existing Bluetooth controls from their regular Home tiles.
4. Confirm that disconnecting from this feature does not alter Bluetooth HID
   pairing or device profiles.
