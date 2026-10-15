# BT Mouse & Keyboard

Turns your Android phone into a Bluetooth touchpad and keyboard for a desktop computer.

## Build
1. Open this folder in Android Studio (Koala or newer). Let it sync Gradle.
2. Plug in your phone (USB debugging on) and press Run, or use Build > Build APK(s).

Requires Android 9 (API 28) or newer.

## Use
1. Open the app and allow the Bluetooth permissions.
2. On your desktop, open Bluetooth settings, search for devices and pair with your phone.
3. Once connected, the status line turns green.
4. Drag on the touchpad to move the pointer. Tap to left click, two-finger tap to right click, two-finger drag to scroll.
5. Tap **Keyboard** to type. Esc, Tab and arrow keys are on the button row.
6. Next time, tap **Devices** and pick your computer to reconnect.

## Notes
- Some phones (a few Samsung, Huawei and older models) ship with the Bluetooth HID Device profile disabled. If the status never says "Ready", that is the likely cause.
- Typing supports letters, digits and common US-layout symbols. Your desktop's keyboard layout should be set to US.
