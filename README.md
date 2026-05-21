# Area Code Blocker

An Android app that silently rejects incoming calls from user-configured area codes — unless the caller is already in your contacts.

Built on Android's `CallScreeningService` API, it intercepts calls *before* your phone ever rings. No missed-call sound, no notification, no interruption — spam callers are simply hung up on.

<img src="https://img.shields.io/badge/Android-10%2B-brightgreen" /> <img src="https://img.shields.io/badge/Kotlin-2.x-blue" /> <img src="https://img.shields.io/badge/Material%20Design-3-purple" /> <img src="https://img.shields.io/badge/License-MIT-yellow" />

---

## Features

- **Configurable area code list** — add or remove any 3-digit area codes. Nothing is blocked by default.
- **Contacts passthrough** — calls from numbers in your contacts are always allowed through, regardless of area code.
- **Silent rejection** — blocked calls are ended before the phone rings. They still appear in your call log.
- **One-tap pause** — master enable/disable switch at the top of the main screen turns blocking off without touching your area code list.
- **Allow-only mode** — flip the logic: block everything *except* calls from your selected area codes and contacts. Toggle in Settings, off by default.
- **Block statistics** — see how many calls were blocked today, in the last 24 hours, last 30 days, and last year.
- **Notification toggle** — enable or disable blocked-call notifications from the main screen.
- **Import / Export** — back up your area codes and block history to a single JSON file; restore after reinstalls or share with others. Old plain-text exports are still supported on import.
- **Dark mode UI** — Material Design 3 card-based interface, always dark.

---

## How It Works

The app registers as the system's **Call Screening** role holder. When an incoming call arrives, Android invokes `onScreenCall()` before the dialer is notified.

**Normal mode (default):**
1. Is the area code in the blocked list?
2. If yes — is the number in the user's contacts?

If the area code matches and the number is *not* in contacts, the call is rejected silently before the phone ever rings.

**Allow-only mode:**
1. Is the area code in the allowed list, or is the number in contacts?
2. If neither — block the call.

Numbers with unrecognizable formats (international, short codes) are always allowed through in either mode.

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
2. Open the app and tap the **⚙** icon → **Grant call-screening role**.
3. Grant **contacts access**.
4. On Android 13+, grant **notifications permission** if you want to be notified when calls are blocked.
5. Add the area codes you want to block (or allow, if using allow-only mode).

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

## Backup File Format

The export file is plain JSON and human-readable:

```json
{
  "version": 1,
  "areaCodes": ["212", "518", "917"],
  "blockedTimestamps": [1715000000000, 1715003600000]
}
```

`blockedTimestamps` are Unix milliseconds. The app retains up to one year of history.

---

## Compatibility Notes

- Coexists with Google's call screening (Pixel) and carrier spam filters. The system runs all registered screening services — a reject from any one ends the call.
- Some OEMs (Samsung, Xiaomi) require you to enable autostart / disable battery optimization for the screening service to remain reliably bound after reboot. If calls stop being blocked after a while, check those settings.

---

## Privacy Policy

[View privacy policy](https://cmcasanova.github.io/area-code-blocker/privacy-policy.html)

---

## License

MIT
