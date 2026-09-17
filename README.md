# BashirConnect — Android

Native port of the [BashirConnect](https://github.com/HopzAlot/BashirConnect) Python script.
Same job (auto-login to the Fortinet `fgtauth` captive portal), but:

- **Event-driven, not polling** — uses `ConnectivityManager.NetworkCallback` +
  `NET_CAPABILITY_CAPTIVE_PORTAL` instead of hitting neverssl.com every 10-60s.
- **Encrypted credentials** — `EncryptedSharedPreferences` (Android Keystore-backed
  AES) instead of a plaintext JSON file.
- **Visible** — a real app with a status screen and activity log, plus a
  low-priority foreground notification, instead of a fully invisible process.

## Project layout

```
MrBashirAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mrbashir/android/
│       │   ├── MainActivity.kt        — Compose UI (setup + live status)
│       │   ├── CaptivePortalService.kt — foreground service, NetworkCallback
│       │   ├── PortalLoginClient.kt    — the actual fgtauth login logic
│       │   ├── CredentialStore.kt      — encrypted username/password storage
│       │   ├── AppStatus.kt            — shared state the UI observes
│       │   └── BootReceiver.kt         — relaunch service after reboot
│       └── res/values/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## How to build

1. Open the `MrBashirAndroid/` folder in **Android Studio** (Koala or newer).
2. Let Gradle sync — it'll pull OkHttp, Compose, and the Security-Crypto library.
3. Run on a device or emulator (minSdk 26 / Android 8.0+).
4. On first launch: type username + password, tap **Save & Start**. The
   foreground service starts, and you'll see log lines appear as networks
   change.

## Known gaps to test on a real phone (things I can't verify from here)

- **`NET_CAPABILITY_CAPTIVE_PORTAL` timing** — on some OEM skins (MIUI, OneUI)
  the OS's own "Sign in to network" notification may race with ours. Worth
  testing whether disabling the stock captive-portal notification (Developer
  Options → "Private DNS" area / or per-OEM Wi-Fi settings) makes ours more
  reliable.
- **Doze mode / battery optimization** — even as a foreground service, some
  OEMs aggressively kill background work. You'll likely need to exempt Mr.
  Bashir in battery settings (Settings → Apps → Mr. Bashir → Battery →
  Unrestricted).
- **`START_STICKY` restart behavior** — service should survive being killed
  and restart, but this needs real-world testing, not just a Kotlin review.

## Not yet ported from the Python version

- `reset_cred` flow — right now "Forget me" clears creds instantly (no
  separate reset script needed since it's all in-app).
- Multi-portal / non-Fortinet support — still hardcoded to `fgtauth`, same
  as the original.

These are good next steps once this scaffold builds and runs cleanly on your phone.
