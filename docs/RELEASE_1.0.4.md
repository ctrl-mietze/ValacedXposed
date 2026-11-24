# ValancedX 1.0.4 — Manager redesign and IPC connection

- Includes the 1.0.3 deferred Discord design lookup fix for the reported Hermes startup error.
- Adds a narrowly scoped Android URI permission for the manager connection, issued only to com.discord, while preserving the provider's Binder caller checks. Addresses the observed unknown authority error; on-device validation is required.
- Redesigned native manager: compact header, persistent bottom navigation, clearer connection states, separate local counters, refresh action and preserved scroll during updates.
- Runtime JS errors now count in diagnostics; provider event writes are serialized.
- Same application ID and release signing configuration. No Discord APK changes or data clearing.
