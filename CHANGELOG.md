# Changelog

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
