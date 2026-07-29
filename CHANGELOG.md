# Changelog

## 2.2.0 — Finalized lyric-first milestone

### Added

- Share Lyrics flow with selectable synchronized lines
- Blush, Midnight, and Cream share-card styles
- On-device 1080×1350 PNG rendering
- Secure cache-only sharing through Android FileProvider
- Dedicated Share action on the Now screen and More sheet
- True dark color schemes and polished lyric-first layout
- Friendly media-app source names
- Lyric-focused notification actions

### Changed

- Lyrics now receive the majority of the Now screen
- Advanced source, timing, notification, and focus controls moved into sheets
- Active lyrics use typography and opacity hierarchy instead of a large highlight block
- Mascot appears only in empty states
- Navigation, typography, surfaces, and controls were simplified
- Version metadata updated to 2.2.0 / versionCode 20200

### Fixed

- Compose crash caused by loading a layer-list as an in-app painter
- Launcher PNG/adaptive-icon separation
- Duplicate theme declarations and incorrect dark-theme construction
- Raw media package names wrapping in track metadata
- Notification action duplication and icon ambiguity
- AUTO/MANUAL/SEARCH interaction structure and source-lock separation

### Quality gate

Every finalized debug build must pass:

```text
testDebugUnitTest
lintDebug
assembleDebug
```

Physical-device and signed-release testing remain required before store publication.

## 1.0.0-final QA rebuild

- enforced Offline Vault save preference
- replaced collision-prone track hash with SHA-256-derived fingerprint
- added strict source locking and session-switch hysteresis
- bounded and hardened lyric network responses
- fixed Android API compatibility in response reading
- preserved coroutine cancellation
- corrected whole-file LRC offset behavior
- rejected malformed second fields
- ensured favourite and sync edits persist for previously uncached tracks
- reduced foreground-service restart and notification churn
- changed notification permission to explicit user-triggered flow
- added dynamic notification actions and direct service stop
- bounded displayed position and deduplicated media controllers
- expanded core unit tests and adversarial QA documentation
