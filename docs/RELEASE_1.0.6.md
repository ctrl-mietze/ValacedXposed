# ValancedX 1.0.6 — One-tap plugin installation

- One tap in Discover resolves and installs the selected plugin and required dependencies via the existing verified installer.
- Keeps the plugin list, current search and scroll position mounted. Progress is shown inline on the selected entry, and the installed/updated status is applied without navigating away.
- Actual download progress and installation stages come from the native installer. Conflicting or downgrade plans are rejected rather than silently breaking existing plugins.
- Fresh plugins stay disabled until explicitly enabled. Discord must be running with the module loaded in the background; otherwise the manager reports a connection timeout and can open Discord.
- Same application ID and signing configuration.
