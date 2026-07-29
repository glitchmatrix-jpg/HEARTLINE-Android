# HEARTLINE for Android — v1.0.0 source release

HEARTLINE detects an active Android media session, retrieves synchronized lyrics, follows playback, stores recent lyrics for offline use, and can keep the current lyric in an ongoing notification.

## What is implemented

- Native Kotlin + Jetpack Compose UI
- Five HEARTLINE themes
- Notification-listener permission flow
- Active `MediaSession` discovery and session scoring
- Play/pause, previous, next, and seeking through the selected player
- LRCLIB HTTPS client with timeouts, rate-limit handling, bounded queries, and a descriptive user agent
- Metadata normalization and candidate scoring
- Tolerant LRC parser and drift-resistant synchronization
- Room-backed Offline Vault
- Configurable retention from 5 to 40 recent songs; default 20
- Protected favourites, pinned tracks, manual matches, and corrected tracks
- Per-track and global timing offsets
- Low-churn ongoing lyric notification
- DataStore settings
- HTTPS-only network policy, no microphone permission, no trackers, no ads
- Unit tests and GitHub Actions APK build

## Build

1. Install Android Studio with JDK 17 and Android SDK 35.
2. Open this folder as a project.
3. Let Gradle sync.
4. Run the `app` configuration on Android 8.0 or newer.

CLI with a local Gradle installation:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Open HEARTLINE.
2. Tap **ENABLE MUSIC ACCESS** and enable HEARTLINE under Notification access.
3. Play a song in Spotify, YouTube Music, Samsung Music, VLC, or another app that publishes a media session.
4. Return to HEARTLINE.
5. Tap **KEEP LYRICS IN NOTIFICATION** to explicitly start background lyric display.

## Security and privacy

- HEARTLINE does **not** request microphone, location, contacts, accounts, or broad file access.
- Cleartext traffic is disabled at the Android network-security layer.
- The only remote request in v1 is an HTTPS lyric lookup to LRCLIB.
- Song history and lyric cache are local Room data.
- Android cloud backup excludes the listening database and artwork cache.
- Notification contents default to private on the lock screen.
- The foreground service begins only after a visible user action.

## Battery strategy

- Media sessions are callback-driven instead of continuously polling other apps.
- The lyric clock ticks every 250 ms only while playing and every 1 s while paused.
- Network lookup occurs once per new fingerprint and then uses Room cache.
- Notification updates happen on state/line changes, not every timer tick.
- Artwork is intentionally not downloaded by this first secure core; this avoids storage and network churn until the artwork cache receives its own bounded implementation.

## Honest v1 limitations

- Android apps that do not expose valid media-session metadata cannot be auto-detected.
- Some browser/video titles need manual correction; the architecture includes fingerprints and saved mappings, but the full candidate-picker screen is the next hardening increment.
- Force-stopping the app in Android Settings stops all services by platform design.
- The foreground service uses Android's `specialUse` type because HEARTLINE displays another app's playback state rather than playing audio itself. Play Store submission requires an accurate foreground-service declaration and policy review.
- This repository does not include signing secrets. Never commit a production keystore.

## Release gate

Do not call a build production-ready until it passes:

- Spotify, YouTube Music, Samsung Music, VLC, and Chrome testing
- Samsung and stock-Android background tests
- process-death and notification-access revocation tests
- network loss/recovery tests
- Android 8 through target-SDK device matrix
- signed release build and dependency/security review
