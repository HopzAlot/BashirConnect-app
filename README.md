# BashirConnect — Android 🧙‍♂️

Auto-login to university Fortinet captive portal (`fgtauth`) campus Wi-Fi.

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-2ea44f?style=for-the-badge&logo=android)](https://github.com/HopzAlot/BashirConnect-app/releases/latest/download/BashirConnect.apk)

> 📲 **Direct Download Link:**  
> **[👉 Click here to download BashirConnect.apk](https://github.com/HopzAlot/BashirConnect-app/releases/latest/download/BashirConnect.apk)**  
> *(Always downloads the latest release directly to your phone — no unzipping or picking versions required!)*

---

## Features

- **Event-driven, not polling** — uses Android's `ConnectivityManager.NetworkCallback` + `NET_CAPABILITY_CAPTIVE_PORTAL` instead of continuously pinging servers.
- **Encrypted credentials** — Keystore-backed AES (`EncryptedSharedPreferences`) stored securely on your device.
- **Modern UI / UX** — Jetpack Compose Material 3 interface featuring Mr. Bashir mascot dialogues, live network status, and statistics dashboard.
- **Edit credentials** — Easily change your Student ID or Password anytime without starting from scratch.
- **Fund Me Jar 🍯** — In-app support jar with JazzCash RAAST QR code to buy the developer a chai!

---

## Quick Install (Phone)

1. Tap the **[Download Latest APK](https://github.com/HopzAlot/BashirConnect-app/releases/latest/download/BashirConnect.apk)** link on your phone.
2. When the `.apk` finishes downloading, tap to open it.
3. Tap **Install** (if prompted by Android, allow "Install unknown apps" for your browser).
4. Open the app, enter your **Student ID** and **Password** once, and Mr. Bashir handles campus Wi-Fi logins automatically!

---

## How to Build (Android Studio)

1. Clone or open the project folder in **Android Studio**.
2. Let Gradle sync.
3. Build and run on any Android 8.0+ device (`minSdk 26`, `targetSdk 34`).
