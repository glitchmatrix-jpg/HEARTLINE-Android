# Changelog

## 2.3.0 — Make the words yours

### Architecture

- Split the active v2.3 interface into focused application-shell, Now, Share, Vault/Settings, and onboarding modules
- Added a single app-theme registry and a single share-card registry
- Replaced raw glyph navigation and playback controls with Material vector icons
- Expanded persisted settings without breaking older saved preferences

### Reliability

- Added listener connection state, active-session count, last media event, and reconnect telemetry to player state
- Added bounded automatic reconnect backoff
- Added a media-session health watchdog
- Refreshes media sessions whenever HEARTLINE returns to the foreground
- Requests Android notification-listener rebinding after service disconnects
- Clears stale track metadata, progress, lyrics, favourite state, and offline state when the live session disappears
- Added one-tap reconnect fallback and a copyable diagnostics report
- Added connection diagnostics UI

### Personalization

- Added Moonlit Lavender, Ocean Static, Paper Heart, Electric Blue, and Matcha Diary
- Added visual theme preview cards
- Added Follow System with independent preferred light and dark themes
- Added OLED-black mode
- Added album artwork and optional artwork backdrop
- Added lyric size, alignment, spacing, bold-active, surrounding-line, and reduced-motion controls

### Share Lyrics 2.0

- Added live card preview
- Added Polaroid, Editorial, CRT, Love Letter, and Cyber card themes
- Added Post, Story, and Square exports
- Added plain-lyrics sharing
- Added searchable full-song selection and nearby-line selection
- Added Save to Pictures and Android Share actions
- Added title, artist, and branding toggles
- Added text-scale and background-intensity controls
- Added adaptive text fitting and Unicode-safe long-token wrapping
- Added local cache cleanup and MediaStore saving

### Lyrics and accessibility

- Manual lyric browsing now pauses auto-follow
- Added a Current lyric return control
- Reduced-motion preference now affects lyric scrolling and content animation
- Added lyric seek semantics, selected-state semantics, content descriptions, and consistent touch targets
- Added proper media and navigation icons

### Offline Vault

- Added song/artist/album search
- Added recent/title/artist sorting
- Added favourites filter
- Added saved-song and favourite counts
- Added stored lyric viewing
- Added favourite, pin/unpin, and removal actions

### Notification and onboarding

- Added current-only, current + next, and song-only notification detail
- Added lock-screen privacy behavior
- Added reconnect notification action when the listener is interrupted
- Added optional media artwork in the notification
- Added first-run connection checklist with live status indicators and test-connection action
- Migrated the live-lyrics notification to the v2.3 channel

### Quality gate

- Kotlin compilation passed
- Unit tests passed
- Android lint passed
- Debug APK assembly passed
- GitHub Actions artifact publication passed

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
