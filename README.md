# HEARTLINE for Android — v2.3.0

**Make the words yours.**

HEARTLINE is a private, native Android lyric companion. It detects the active media session, finds synchronized lyrics through LRCLIB, follows playback, stores recent lyrics for offline use, displays a live lyric notification, and creates customizable lyric images entirely on-device.

## v2.3 highlights

### Reliable music detection

- Automatic `NotificationListenerService` rebinding after Android disconnects it
- Bounded reconnect backoff and media-session health watchdog
- Active-session refresh whenever HEARTLINE returns to the foreground
- Stale track and lyric state cleared when the live session disappears
- One-tap reconnect fallback
- Connection diagnostics showing listener state, active sessions, selected source, metadata health, last event age, and reconnect attempts
- Copyable diagnostic report

### Personalization

HEARTLINE now includes ten registered app themes:

- Bubblegum
- Cyber Angel
- Cherry Soda
- Haunted CRT
- Peach Dream
- Moonlit Lavender
- Ocean Static
- Paper Heart
- Electric Blue
- Matcha Diary

The theme browser shows real color previews. HEARTLINE can follow the Android system theme with separate preferred light and dark themes, and dark palettes support an optional OLED-black background.

Lyric appearance controls include:

- Small, Standard, Large, and Extra Large text
- Center or left alignment
- Comfortable or compact spacing
- Bold active lyric toggle
- One, two, or three prominent surrounding lines
- Reduced-motion behavior

### Share Lyrics 2.0

- Live image preview before export
- Eight card styles: Blush, Midnight, Cream, Polaroid, Editorial, CRT, Love Letter, and Cyber
- Post `1080×1350`, Story `1080×1920`, and Square `1080×1080` formats
- Share synchronized or plain lyrics
- Nearby-line and searchable full-song selection
- Select up to five lines
- Optional title, artist, and HEARTLINE branding
- Adjustable text size and background intensity
- Adaptive text fitting for long lyrics
- Unicode-safe splitting for oversized words
- Save to `Pictures/HEARTLINE` or share through Android’s system share sheet

### Lyric experience

- Manual browsing pauses automatic lyric following
- A **Current lyric** control returns to live playback
- Album artwork supplied by the active media app can appear in the track header
- Optional soft artwork backdrop
- Proper vector playback and navigation icons
- TalkBack-aware lyric seek controls and selected-state semantics

### Offline Vault

- Search by song, artist, or album
- Sort by recent, title, or artist
- Favourites-only filter
- Open stored lyric text
- Favourite, pin, unpin, and remove tracks
- Saved-song and favourite counts

### Notification and privacy

- Current lyric, current + next, or song-only notification detail
- Reconnect action when the music listener is interrupted
- Optional media artwork when Android grants access to it
- Configurable lock-screen privacy
- Sync earlier, reset, and later actions while connected
- HTTPS-only networking, no microphone permission, no analytics, no trackers, and no ads

## Architecture

The v2.3 interface is split into focused modules under `ui/v23/`:

```text
ui/v23/
  HeartlineV23App.kt
  NowScreenV23.kt
  ShareLyricsV23.kt
  VaultSettingsV23.kt
  OnboardingV23.kt
```

Theme definitions live in a single registry. Share-card styles and output formats also use a centralized registry, so future additions do not require duplicating names and rendering logic throughout the app.

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

GitHub Actions runs the same unit-test, lint, and debug-assembly gate and publishes the `HEARTLINE-debug-apk` artifact.

## First run

1. Install and open HEARTLINE.
2. Complete the live connection checklist.
3. Enable HEARTLINE under Android **Notification access**.
4. Play music in Spotify, YouTube Music, Symfonium, Samsung Music, VLC, Poweramp, or another app that exposes a media session.
5. Return to HEARTLINE and tap **Test connection** if the song has not appeared yet.
6. Use **Choose** to select another lyric version when needed.
7. Use the offset control to adjust synchronization.
8. Tap the Share icon to create a lyric image.
9. Enable **Live lyric notification** from More or Settings when desired.

Sideloaded Android builds may require **Allow restricted settings** before Notification access can be enabled. Only grant this to an APK you built or otherwise trust.

## Share Lyrics privacy model

Share cards are rendered entirely on-device with Android Canvas. Images selected for sharing are written to the app’s private cache and exposed temporarily through a non-exported `FileProvider`. Images explicitly saved by the user are written through Android MediaStore to `Pictures/HEARTLINE` on Android 10 or newer. HEARTLINE does not upload selected lyrics or generated images to a HEARTLINE server.

## Security and privacy

- No microphone, location, contacts, accounts, or broad storage permission
- Cleartext traffic disabled at the Android network-security layer
- Lyric requests use HTTPS through LRCLIB
- Listening history and lyric cache remain in the local Room database
- No analytics SDK, advertising SDK, or third-party tracker
- Notification lock-screen visibility is configurable
- The foreground service starts only after explicit user action

## Battery strategy

- Media sessions are callback-driven rather than continuously polling other apps
- The reconnect watchdog runs at a bounded interval and only requests repairs when needed
- The lyric clock ticks more frequently only while playback is active
- Network lookup occurs once per new fingerprint before using the local cache
- Notification updates happen on meaningful state or lyric changes
- Full-resolution share-card rendering occurs only when the user taps Save or Share

## Release status

Version 2.3.0 is a debug-distribution milestone. The automated gate covers compilation, unit tests, Android lint, APK assembly, and artifact publication.

Store publication still requires:

- signed release build and protected keystore handling
- physical-device checks across supported Android versions and manufacturers
- Spotify, YouTube Music, Symfonium, Samsung Music, VLC, Poweramp, and browser session checks
- process-death, permission-revocation, network-loss, and background-service checks
- Play Store foreground-service declaration and policy review

Never commit a production signing keystore or signing credentials.
