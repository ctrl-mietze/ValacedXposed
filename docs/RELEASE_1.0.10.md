# ValancedX 1.0.10 — Theme Studio and reliable manager

- No replacement of the list while a plugin, repository, or theme action runs; inline state and scroll anchor restoration.
- Compact installed-source rows, redesigned segments, Discord root restart from the manager.
- Dedicated request-correlated Discord Theme IPC, persistent cached manager snapshots, and explicit original/selected colors, font, icon, and emoji states.
- Eight original Veyra Discord palette designs, resolved theme color previews and verified catalog palette previews.
- Spec 1 TTF/OTF font-pack import, legacy Spec 2 color migration, background URL and opacity/blur/dimming controls.
- Three animated gradient backgrounds through the existing Valenced background renderer; animation stops in background. No MP4/WebM playback.
- Theme engine still depends on compatible Discord/Valenced hooks. Existing package ID and release signature retained.

- Fix dependency cascade diagnostics to report every required failure regardless of directory ordering. Adds regression coverage without skipping existing tests.
