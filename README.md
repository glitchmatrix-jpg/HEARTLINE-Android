# HEARTLINE for Android — v2.2.0

HEARTLINE is a private, native Android lyric companion. It detects the active media session, finds synchronized lyrics through LRCLIB, follows playback, stores recent lyrics for offline use, displays a live lyric notification, and creates shareable lyric image cards.

## Highlights

- Lyric-first Jetpack Compose interface with compact controls and progressive disclosure
- AUTO mode for real-player control, MANUAL mode for an independent lyric clock, and SEARCH mode for manual lyric matching
- Active Android media-session discovery and source locking
- LRCLIB candidate search, alternate-version picker, and saved per-song matches
- Drift-resistant synchronized lyrics with per-track timing correction
- Room-backed Offline Vault with configurable retention
- Favourites and offline-ready lyric storage
- True light and dark HEARTLINE themes
- Immersive Focus mode
- Lyric-first ongoing notification with earlier/reset/later sync actions
- **Share Lyrics:** select up to five synchronized lines, choose Blush, Midnight, or Cream, generate a 1080×1350 HEARTLINE image, and share through Android’s system share sheet
- Original HEARTLINE launcher artwork, adaptive icon, round icon, themed monochrome icon, and notification icon
- HTTPS-only networking, no microphone permission, no trackers, and no ads

## Build requirements

- Android Studio or Gradle with JDK 17
- Android SDK 35
- Minimum Android version: Android 8.0 / API 26

Run the complete local quality gate:

```bash
gradle testDebugUnitTest lintDebug assembleDebug --stacktrace
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the same test, lint, and debug-assembly gate and publishes the `HEARTLINE-debug-apk` artifact.

## First run

1. Open HEARTLINE.
2. Enable HEARTLINE under Android **Notification access** when prompted.
3. Play music in an app that exposes a media session, such as Spotify, YouTube Music, Symfonium, Samsung Music, VLC, or Poweramp.
4. Return to HEARTLINE and allow it to find lyrics.
5. Open **Choose** to select another lyric version when needed.
6. Open **Sync** to adjust timing.
7. Tap **Share** to select lyric lines and create a shareable image.
8. Enable **Live lyric notification** from More or Settings when desired.

## Share Lyrics privacy model

Share cards are rendered entirely on-device using Android Canvas. The generated PNG is written only to the app’s cache directory and exposed temporarily through a non-exported `FileProvider`. HEARTLINE does not upload the image or selected lyrics to a HEARTLINE server. Old cached share images are cleaned automatically.

## Security and privacy

- No microphone, location, contacts, accounts, or broad storage permission
- Cleartext traffic disabled at the Android network-security layer
- Lyric requests use HTTPS through LRCLIB
- Listening history and lyric cache remain in the local Room database
- Share images use app-private cache storage and temporary URI grants
- No analytics SDK, advertising SDK, or third-party tracker
- Notification contents use private lock-screen visibility
- The foreground service starts only after an explicit user action

## Battery strategy

- Media sessions are callback-driven rather than continuously polling other apps
- The lyric clock ticks more frequently only while playback is active
- Network lookup occurs once per new fingerprint before using the local cache
- Notification updates happen on state or lyric-line changes rather than every timer tick
- Share-card rendering occurs only when the user taps **Create and share image**

## Project structure

- `media/` — Android media-session detection and transport control
- `lyrics/` — LRCLIB client, matching, parsing, and synchronization
- `data/` — Room entities, DAOs, settings, and player state
- `service/` — foreground live-lyrics notification
- `share/` — local lyric-card rendering and secure Android sharing
- `ui/` — Compose interface, sheets, Vault, and settings

## Release status

Version 2.2.0 is the finalized debug-distribution milestone. The automated gate covers compilation, unit tests, lint, and APK assembly. Production publication still requires:

- signed release build and protected keystore handling
- physical-device checks across supported Android versions and manufacturers
- Spotify, YouTube Music, Symfonium, Samsung Music, VLC, Poweramp, and browser media-session checks
- process-death, permission-revocation, network-loss, and background-service checks
- Play Store foreground-service declaration and policy review

Never commit a production signing keystore or signing credentials.
