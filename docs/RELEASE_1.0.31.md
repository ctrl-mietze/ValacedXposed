# Vexcord 1.0.31 — BitChord Presence 1.1

- Automatic connection to the already logged-in Discord client. No credential extraction, copying or separate login.
- Current-user detection and native client activity dispatcher. Publish song and artist before loading optional artwork; use the client's application asset utility and existing authenticated asset endpoint.
- Distinguish connection, submission and local presence observation. Reconcile on reconnect and bounded retry when an activity is not locally observed.
- Optional paused-track activity, accurate playback state, independent cover diagnostics and sanitized diagnostic report in plugin details.
- Existing Stereo Call 1.1.0 native engine included. No changes to the other plugin sources or installed settings.
- A connected MediaBrowser and local dispatch are not proof of remote Discord profile visibility. Actual playback and profile visibility require testing on the device.
