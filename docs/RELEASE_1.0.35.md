# Vexcord 1.0.35 — Stereo Call startup recovery

- Repair a missing Discord-local AudioManager application context before setMode, including with Stereo Call disabled.
- Avoid early AudioManager acquisition during application attachment; preserve the original requested audio mode.
- Restore optional routes on native disable even if the JavaScript lifecycle fails. Cancel queued LE requests, release owned routes and revoke the separate media routing lease.
- Stereo Call 1.4.0 is available through the first-party plugin repository. Its settings indicate whether the new crash guard is present.
- Increase the Hermes compiler output buffer without ignoring compilation failures.
- Preserve the 1.0.34 plugin-settings navigation changes and existing plugins.

The crash guard is process-local. Bluetooth routing still depends on Android and headset support; the experimental OpenSL ES media route is not reported as verified without evidence.
