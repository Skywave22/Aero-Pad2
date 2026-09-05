# AeroPad Remote

AeroPad is a powerful, low-latency remote control application that transforms your Android device into a wireless trackpad, keyboard, gamepad, and presentation remote for your PC.

Designed with a sleek, flat Material 3 interface based on the organic **Earthy Sand** aesthetic, AeroPad connects instantly via Bluetooth, requiring no external server or dongle software to run on the host computer.

## Features
- **Trackpad:** Gestures, precision mode, scroll strips, and high-fidelity movement smoothing.
- **Keyboard:** Standard layout, modifier keys, media controls, and integrated system commands.
- **Gamepad:** Turn your phone into an XInput/DInput-style gamepad for casual gaming.
- **Bluetooth-Only Architecture:** Zero Wi-Fi latency or firewall issues. It works completely offline directly to the PC's Bluetooth stack.
- **Organic Design:** A fully flattened, tactile interface using warm sand tones, devoid of distracting 3D shadows and tilts.

## Tech Stack
- **Kotlin & Jetpack Compose**
- **Dagger/Hilt** (Dependency Injection)
- **Room & DataStore** (Persistence)
- **Android Bluetooth HID Device API**

## Building the Project
1. Clone this repository.
2. Open the project in Android Studio.
3. Build and Run! 

*Note: A real Android device is required to test Bluetooth HID profiles (the emulator does not support it).*
