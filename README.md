# Area Code Spammer Trap

An Android app that silently rejects incoming calls from user-configured area codes — unless the caller is already in your contacts.

Built on Android's `CallScreeningService` API, it intercepts calls *before* your phone ever rings. No missed-call sound, no notification, no interruption — spam callers are simply hung up on.

<img src="https://img.shields.io/badge/Android-10%2B-brightgreen" /> <img src="https://img.shields.io/badge/Kotlin-2.x-blue" /> <img src="https://img.shields.io/badge/Material%20Design-3-purple" /> <img src="https://img.shields.io/badge/License-MIT-yellow" />

---

## Features

- **Configurable area code list** — add or remove any 3-digit area codes you want to block. Nothing is blocked by default.
- **Contacts passthrough** — calls from numbers already in your contacts are always allowed through, regardless of area code.
- **Silent rejection** — blocked calls are ended before the phone rings. They still appear in your call log so you can see who tried.
- **Blocked call notifications** — optional notification each time a call is rejected, showing the number.
- **Import / Export** — save your area code list to a plain text file (one code per line) and restore it after reinstalls or share it with others.
- **Material Design 3 UI** — clean card-based interface with automatic light/dark mode support.

---

## How It Works

The app registers as the system's **Call Screening** role holder. When an incoming call arrives, Android invokes `onScreenCall()` before the dialer is notified. The app checks:

1. Is the area code in the blocked list?
2. If yes — is the number in the user's contacts?

If the area code matches and the number is *not* in contacts, the call is rejected with `setDisallowCall(true)` + `setRejectCall(true)` — silently and instantly, before the phone ever rings.

Failure is fail-safe: if the contacts permission is missing or a query throws, the call is allowed through rather than risk blocking someone important.

---

## Requirements

- Android 10 (API 29) or later
- Call Screening role (granted via in-app prompt)
- Contacts permission (to allow contacts through)
- Notifications permission (Android 13+ only, for blocked-call notifications)

---

## Setup

1. Install the APK or build from source.
2. Open the app and tap **Grant call-screening role** — Android will prompt you to confirm.
3. Tap **Grant contacts access**.
4. On Android 13+, tap **Grant notifications permission** if you want to be notified when calls are blocked.
5. Add the area codes you want to block.

The app works in the background with no ongoing notification required.

---

## Building from Source

```bash
git clone https://github.com/cmcasanova/area-code-blocker.git
cd area-code-blocker
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires Android Studio Hedgehog or newer (or a standalone SDK with Gradle). JDK 17.

| | |
|---|---|
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 34 (Android 14) |
| **Language** | Kotlin |

---

## Permissions

| Permission | Why |
|---|---|
| `READ_CONTACTS` | Allows calls from your contacts through regardless of area code |
| `POST_NOTIFICATIONS` | Shows a notification when a call is blocked (Android 13+) |
| `BIND_SCREENING_SERVICE` | Required by the system to bind the `CallScreeningService` |

---

## Compatibility Notes

- Coexists with Google's call screening (Pixel) and carrier spam filters. The system runs all registered screening services — a reject from any one ends the call.
- Some OEMs (Samsung, Xiaomi) require you to enable autostart / disable battery optimization for the screening service to remain reliably bound after reboot. If calls stop being blocked after a while, check those settings.

---

## License

MIT
