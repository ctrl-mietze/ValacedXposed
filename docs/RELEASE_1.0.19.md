# ValancedX 1.0.19 — Plugin Lab and Vencord Translator

- Per-plugin and batch analysis of installed and repository plugins, local reports keyed to version/artifact.
- Read-only export of declared installed scripts; bounded download with catalog size/SHA-256 verification. APK assets contain internal plugin entry sources.
- Separate non-exported Android isolated service instance per scan on API 29+; no Discord account data, root, filesystem permissions, or network in sandbox. ZIP parsing is bounded; native binaries never executed.
- Static API evidence and limited Rhino interpreted mock JS lifecycle with Java access denied and instruction/deadline limits. Unsupported syntax/APIs and native artifacts reported honestly. Not a security certification.
- Vencord catalog pinned to ce9355e9a93e369c7295fa162ad563f7f97b1ff7 (Vendicated/Vencord), metadata and entrypoint SHA-256 only. Original desktop code is fetched for analysis, never installed or executed in Discord.
- Five rule-based feature adapters automatically activate corresponding integrated mobile ports after source hash and sandbox checks: SilentTyping, CharacterCounter, ClearURLs, ImageLink, AlwaysAnimate. Partial behavior documented; other plugins require a new adapter. No universal Webpack/CSS-to-Metro compiler.
- Existing Legacy master mode and conflict detection retained. Same package and signing.
