# HEARTLINE Android v1.0.0 — Adversarial QA Report

## Scope

This audit reviewed the complete Android source tree for architecture, Kotlin correctness, Android lifecycle behavior, media-session selection, lyric retrieval, synchronization, offline persistence, notification behavior, privacy, network security, battery use, build reproducibility, and failure handling.

## Verification completed in this environment

- ZIP integrity and complete source-tree extraction
- XML parsing for every manifest/resource XML file
- Kotlin delimiter and source-tree structural checks
- Compilation and execution of the platform-independent lyric parser and metadata normalizer
- Core logic tests covering timestamp parsing, multiple timestamps, offset tags, malformed seconds, binary-search line selection, metadata cleanup, and stable SHA-256 track fingerprints
- Static permission audit
- Cleartext-network audit
- hard-coded secret/token scan
- TODO/FIXME scan
- foreground-service declaration review
- Room/DataStore persistence review
- callback, cancellation, and coroutine lifecycle review
- offline-retention and protected-item eviction review
- notification-update frequency review

## High-severity defects corrected

1. **Offline toggle did not actually disable new lyric persistence.** New results now remain transient when automatic offline saving is disabled.
2. **Track fingerprints used a 32-bit hash.** Replaced with a stable 128-bit prefix of SHA-256 to substantially reduce collision risk.
3. **Manual source lock was not strict.** The lock now filters sessions by package and clearly reports when the locked source disappears.
4. **Player switching could flap between sessions.** Added score hysteresis and a switching margin.
5. **Favouriting or correcting an uncached track silently failed.** HEARTLINE now creates the minimal local row before updating favourite or offset fields.
6. **Network responses were unbounded.** Added a 2 MiB response ceiling, redirect refusal, HTTPS enforcement, query limits, and bounded result counts.
7. **Java `InputStream.readNBytes` risked failing on older supported Android versions.** Replaced with an API-safe bounded stream reader.
8. **Cancellation could be swallowed as a generic lyric error.** Coroutine cancellation is now rethrown correctly.
9. **LRC offset tags only affected lines appearing after the tag.** Offset is now discovered first and applied consistently to the entire file.
10. **Malformed timestamps with seconds above 59 were accepted.** They are now ignored.
11. **Foreground service restarted with `START_STICKY`.** Changed to `START_NOT_STICKY` to avoid unwanted resurrection and battery use.
12. **Notification updates reacted to every player-state tick.** Added a distinct notification model so updates occur only when visible notification content changes.
13. **Notification permission was requested immediately at startup.** It is now requested only when the user enables background lyric notifications.
14. **Stopping the service could accidentally start it first.** The activity now calls `stopService` directly.
15. **Notification playback icon was always pause.** It now reflects current play/pause state and includes sync, favourite, and stop actions.
16. **Displayed position could exceed track duration.** Position is now bounded when duration is known.
17. **Duplicate media controllers could be registered.** Active controllers are deduplicated by session token.

## Security findings

- No microphone, location, contacts, account, broad-storage, camera, or Bluetooth permissions.
- No analytics, advertising, tracking, or crash-reporting SDKs.
- No API keys, passwords, signing credentials, or access tokens in source.
- Cleartext traffic disabled in both manifest and network security configuration.
- LRCLIB requests are explicitly checked for HTTPS.
- Redirects are refused.
- Remote content is handled as text and JSON only.
- Services are non-exported; notification listener uses the system binding permission.
- Pending intents are immutable.
- Listening database is excluded from cloud backup and device transfer.
- Release shrinking and minification remain enabled.

## Power findings

- Media changes are callback-driven rather than repeatedly scanning installed applications.
- UI lyric position updates at 250 ms only while actively playing and 1 s while paused.
- Network lookup occurs only on a new track fingerprint and uses the local cache thereafter.
- Foreground notification updates are deduplicated by visible content.
- No microphone recognition, wake lock, permanent screen-on flag, location service, or periodic worker.
- Offline storage remains capped at 5–40 automatic entries, with protected entries exempt from eviction.
- Foreground service is user initiated and non-sticky.

## Remaining device-validation gate

This container does not include a complete Android SDK/emulator/device environment and cannot truthfully certify physical-device behavior. The repository therefore still requires the included GitHub Actions build and real-device matrix before public distribution.

Required acceptance devices/apps:

- stock Android / Pixel-like device
- Samsung One UI device
- Android 8, 10, 13, 14, and 15 coverage where practical
- Spotify
- YouTube Music
- VLC
- Samsung Music
- Chrome media sessions

Required manual scenarios:

- permission grant/revoke while running
- multiple simultaneous sessions
- pause, resume, seek, next, previous
- Wi-Fi loss and cached lyric continuation
- Wi-Fi-only policy while using mobile data
- app removed from Recents
- force stop
- battery saver and Samsung background restrictions
- service start with notification permission denied
- process recreation and device sleep

## Release judgment

**Source QA status: PASSED WITH DEVICE GATE.**

The corrected source is materially safer, more deterministic, and more power-conscious than the initial package. No honest audit can promise flawless behavior across Android manufacturers without compiling and exercising the APK on real devices. This package is ready for CI compilation and device acceptance testing, not yet a Play Store production declaration.
