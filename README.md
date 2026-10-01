# BashirConnect — Android

Auto-login to the Fortinet captive portal Wi-Fi at **NUST CEME**.

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-2ea44f?style=for-the-badge&logo=android)](https://github.com/HopzAlot/BashirConnect-app/releases/latest)

> **Download Latest Release:**
> **[Click here to download BashirConnect APK](https://github.com/HopzAlot/BashirConnect-app/releases/latest)**
> *(Tap the `.apk` file under Assets to install.)*

---

## What is this?

I got tired of signing into the NUST CEME campus Wi-Fi portal every single time I switched networks or my session expired. So I built BashirConnect — you enter your Student ID and password once, and Mr. Bashir (your personal Wi-Fi assistant) handles all the sign-ins automatically from that point on, while enjoying his chai.

This app is built specifically for the **Fortinet `fgtauth` captive portal at NUST CEME**. It may work on other Fortinet-based university portals, but that is not tested.

**On Windows or Linux?** Check out the original Python version:
[github.com/HopzAlot/BashirConnect](https://github.com/HopzAlot/BashirConnect)

---

## Features

- **Auto-login** — detects captive portal networks and logs you in automatically, no manual sign-in needed.
- **Encrypted credentials** — your Student ID and password are stored using Android Keystore-backed AES encryption, never in plain text.
- **Edit credentials anytime** — change your Student ID or Password without starting from scratch.
- **Modern UI** — Jetpack Compose Material 3 with live status, activity log, and the Mr. Bashir mascot.
- **Fund Me Jar** — in-app JazzCash RAAST QR if you want to help cover the Play Store publishing fee (it is $25 and I am broke).

---

## Quick Install

1. Tap **[Download Latest APK](https://github.com/HopzAlot/BashirConnect-app/releases/latest)** on your phone.
2. Under **Assets**, tap `BashirConnect-v*.apk` to download.
3. Tap **Install** — if Android flags it as potentially unsafe, install it anyway. It is not on the Play Store because I cannot afford the $25 fee, not because it is malicious.
4. Enter your **Student ID** and **Password** once. Mr. Bashir takes it from there.

---

## How to Build (Android Studio)

1. Clone or open the project folder in **Android Studio**.
2. Let Gradle sync.
3. Build and run on any Android 8.0+ device (`minSdk 26`, `targetSdk 34`).
