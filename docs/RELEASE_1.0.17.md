# ValancedX 1.0.17 — Live Discord connection

- Visible manager holds an unstable, non-file provider reference to Discord's main process using an exact host-authorized URI. The reference is released on stop. No file operations, prefix grants, root commands, or system power settings.
- Authenticated native heartbeat reports worker PID, version, policy and JS dispatcher readiness. Header shows live connectivity.
- ContentObserver signals pending operations immediately, with short foreground polling fallback and independent heartbeats during installs.
- Reconnect on foreground/resume; one read-only resync per view/host session. Existing cached lists remain visible.
- Android may still kill Discord or require opening its UI to start React Native. No claim of an unkillable process or automatic app-screen takeover.
- Same package and signing key.
