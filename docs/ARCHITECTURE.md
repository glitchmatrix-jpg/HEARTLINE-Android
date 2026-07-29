# Architecture

`NotificationListenerService` grants access to active media controllers. `MediaSessionRepository` scores controllers, exposes one shared `PlayerState`, and drives both Compose and the foreground notification. `LyricsRepository` checks Room first, then performs a bounded LRCLIB search, parses LRC, persists the result, and enforces the Offline Vault retention policy. `SettingsRepository` uses DataStore.

The UI and notification never run separate lyric clocks. That single-source-of-truth rule prevents drift.
