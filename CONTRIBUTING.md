# Contributing to Vexcord

Keep changes reviewable and preserve existing plugin IDs, source records, storage formats and public compatibility contracts. Use a separate branch for each feature.

Run `./gradlew :android:assembleDebug :android:testDebugUnitTest :bundle:testBundle`. SDK and compiler fixture tests live under `tools/`. New translator rules must have a pinned source pattern, a typed operation, explicit mobile limitations, reversible hooks and deterministic tests.

Do not commit signing keys, credentials, account data, or device logs containing personal content. Bug reports should include the Vexcord/Discord versions, framework version, a minimal reproduction and sanitized diagnostics. GPL-3.0 applies to contributions under this project's license.

For plugin development, begin with [SDK.md](docs/SDK.md). Preserve the license and attribution of any third-party code.
