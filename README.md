# 🎮 MK1300 V2 Android Controller

A custom, native Android application built completely from scratch to control the RGB lighting of the **Ant Esports MK1300 V2** 60% mechanical keyboard via USB-OTG.

No PC required. Just plug the keyboard into your Android phone via an OTG adapter, open the app, and take full control of your lighting.

## ✨ Features

* **Global Effects Control:** Change the entire keyboard's lighting mode, brightness, speed, and base color.
* **Per-Key RGB Matrix:** An interactive, horizontally scrolling 60% keyboard layout on your phone screen. Tap any key to assign it a specific color.
* **Hex & RGB Input:** Choose colors using the Red/Green/Blue sliders or type in the exact Hex code (e.g., `#FF00FF`).
* **Preset Themes:** Instantly apply complex, color-coded themes (like Cyberpunk, Dracula, Matrix, and Vaporwave) across the keyboard.
* **"Try Your Luck":** Generates a completely random color palette for the entire keyboard.

## 📱 How to Install (For Users)

1. Go to the **Releases** tab on the right side of this GitHub repository.
2. Download the latest `mk1300-controller-apk.zip` file.
3. Extract the `.zip` file on your phone or PC to get the `app-debug.apk`.
4. Transfer the `.apk` to your Android phone.
5. Tap the file to install it. (You may need to enable **"Install from Unknown Sources"** in your phone's settings).
6. Plug your MK1300 V2 keyboard into your phone using a **USB-OTG adapter**.
7. Open the app, tap **Scan & Connect**, and grant the USB permission when prompted.

*Note: If your phone does not detect the keyboard, make sure your OTG adapter supports data transfer and that your phone's USB port is functioning correctly.*

## 🛠️ How it Works (Under the Hood)

This app does not use the official Ant Esports software. It communicates directly with the keyboard's microcontroller using **Android's USB Host API**.

The keyboard exposes a vendor-specific HID interface. The app bypasses the standard typing interface and sends raw 64-byte packets directly to the RGB controller.

* **Global Commands:** Uses packet structure `[6, 11, ...]` to change modes and base colors.
* **Per-Key Commands:** Uses a chunked packet structure `[6, 18, ...]` to send RGB data for all 61 keys (119 hardware indices) rapidly without overloading the keyboard's memory buffer.

## 💻 Building from Source (For Developers)

This repository uses **GitHub Actions** to compile the APK in the cloud, meaning you don't need Android Studio installed to build it.

If you want to build it yourself:
1. Fork this repository.
2. Make your changes to the Kotlin/XML files.
3. Commit and push to the `main` branch.
4. Go to the **Actions** tab, click on the latest `Build APK` run, and download the APK from the **Artifacts** section.

### Tech Stack

* **Language:** Kotlin
* **UI:** Android XML Layouts
* **Architecture:** Native Android USB Host API (`UsbManager`, `UsbDeviceConnection`)
* **CI/CD:** GitHub Actions

## ⚠️ Safety & Disclaimer

* **Do NOT** unplug the keyboard while the app is actively sending a large batch of color packets. Let the app finish sending the data.
* This app is an independent, community-made project. It is **not** affiliated with or endorsed by Ant Esports.
* Use at your own risk. While the packets sent are safe and follow the hardware's expected protocol, the developer is not responsible for any hardware malfunctions.

## 🙏 Credits & Acknowledgements

* **Protocol Reverse Engineering:** Massive thanks to **[kicktroll](https://github.com/kicktroll)** for open-sourcing his WebHID driver. The exact key matrix mappings and HSL color generation logic were ported directly from his open-source project.
* **Developed By:** [Ishan](https://github.com/)
* **YouTube:** [Gaming with Ishan](https://youtube.com/@gamingishan143)

---

*If you found this project helpful, consider starring the repository and subscribing to the YouTube channel!*
