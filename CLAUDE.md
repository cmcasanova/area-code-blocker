# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Release Commands

```bash
# Debug build (no signing config needed)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Signed release APK (for GitHub releases & sideload)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Signed AAB (for Play Store ONLY — never upload to GitHub)
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Release signing reads from `keystore.properties` at repo root (gitignored). If absent, release builds fall through with `storeFile = null` and produce an unsigned APK.

There are no unit/instrumented tests in this project.

## Release Workflow

When shipping a new version:

1. Bump `versionCode` and `versionName` in `app/build.gradle`
2. `./gradlew assembleRelease`
3. Commit with a message starting `vX.Y: <summary>`, push to `main`
4. Create a GitHub release tagged `vX.Y` and attach `app-release.apk` (renamed to `area-code-blocker-vX.Y.apk`)
5. **Update `README.md`** to reflect new features and push as a separate commit — this is mandatory after every version push
6. **Never** attach `.aab` files to GitHub releases — those are private Play Store uploads

The GitHub token is stored at `~/.github_token` (chmod 600). Access it via `$(cat ~/.github_token | tr -d '[:space:]')`. **Never paste the token into chat or commits** — GitHub's secret scanner auto-revokes leaked tokens.

## Architecture

This is a small two-component Android app:

- **`AreaCodeScreeningService`** (`app/src/main/java/.../AreaCodeScreeningService.kt`) — extends Android's `CallScreeningService`. Registered as the system's Call Screening role holder so Android invokes `onScreenCall()` before the dialer rings. Makes the allow/reject decision and posts the blocked-call notification.
- **`MainActivity`** (`app/src/main/java/.../MainActivity.kt`) — the only Activity. Manages the area code list, permissions UX (bottom sheet), statistics display, settings toggles, and JSON import/export.

Both communicate exclusively through `SharedPreferences` (`area_code_prefs`). The package-level constants in `AreaCodeScreeningService.kt` are the contract between the two:

| Key | Type | Purpose |
|---|---|---|
| `PREF_AREA_CODES` | `Set<String>` | 3-digit codes the user has added |
| `PREF_BLOCKED_TIMESTAMPS` | `Set<String>` | Epoch-ms timestamps of blocks; pruned to last 1 year on each new entry |
| `PREF_NOTIFICATIONS_ENABLED` | `Boolean` (default `true`) | Whether the service posts notifications |
| `PREF_REVERSE_MODE` | `Boolean` (default `false`) | Flips blocking logic (see below) |

### Blocking logic

Two modes, both fail-safe (errors always result in `respondAllow`):

- **Normal mode**: block iff `areaCode ∈ PREF_AREA_CODES` AND number not in contacts
- **Allow-only mode** (`PREF_REVERSE_MODE = true`): block iff `areaCode ∉ PREF_AREA_CODES` AND number not in contacts

`extractAreaCode()` only handles 10-digit and 11-digit-leading-1 (NANP). Numbers it can't parse (international, short codes) are always allowed through in either mode.

### Backup file format

`MainActivity.exportData()` writes a single JSON document containing both area codes and the last year of block timestamps. `MainActivity.importData()` sniffs the first non-whitespace character: `{` → JSON; otherwise treated as the legacy plain-text format (one area code per line) for backwards compatibility.

## UI Constraints

- The app is **always dark** (`Theme.Material3.Dark.NoActionBar`). Do not reintroduce `DayNight` themes — text color regressions caused real bugs previously.
- Most colors in `activity_main.xml` are **hardcoded hex** rather than theme attributes. This was a deliberate fix for Material3 attribute-resolution producing illegible grey text. Keep it that way unless you've verified the theme attributes resolve correctly on the target SDKs.
- `MainActivity` extends `ComponentActivity`, not `AppCompatActivity`. The toolbar is wired up manually via `setOnMenuItemClickListener`.

## Privacy Policy

GitHub Pages serves the privacy policy from `docs/privacy-policy.html` at `https://cmcasanova.github.io/area-code-blocker/privacy-policy.html`. Pages source is configured to `main` branch, `/docs` folder. If the app gains new permissions or data flows, update this page.

## Configuration

- **Package**: `com.example.areacodeblocker`
- **Min SDK**: 29 (Android 10 — required for `CallScreeningService`)
- **Target SDK**: 34
- **Java/Kotlin**: 17
