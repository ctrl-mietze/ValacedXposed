# Vencord Compiler

The compiler reads a checked-out Vencord revision, parses every tracked TS/TSX/JS file under src/plugins with TypeScript 5.9.3, inventories user and internal definitions, and emits the Android catalog, Kotlin rule table, compatibility matrix and bundled typed-IR adapters.

## Rebuild

From the ValancedX repository root, with Node and npm installed:

- Check out Vendicated/Vencord at commit ce9355e9a93e369c7295fa162ad563f7f97b1ff7 in a separate directory.
- Install TypeScript 5.9.3 in a separate tooling directory with npm install --ignore-scripts --save-exact typescript@5.9.3.
- Set VX_TYPESCRIPT to the absolute path of that node_modules/typescript directory.
- Run node tools/vencord-compiler/compile.mjs /path/to/Vencord . tools/vencord-compiler/mobile-lock.json.

The output is a complete offline static inventory, not a proof that each plugin runs. Existing Android equivalents are separately labelled and pinned to checked published artifacts. The two generated IR operations use independently implemented mobile hooks, rather than copying Vencord's executable bundle. Runtime checks log the mobile module bindings and restore state on disable.

Original Vencord source and GPL-3.0-or-later licensing: https://github.com/Vendicated/Vencord/tree/ce9355e9a93e369c7295fa162ad563f7f97b1ff7. No upstream desktop bundles are included in the APK. The lock file records public plugin catalog metadata and hashes, not device settings or account data.

To extend the compiler, add a new recognized AST pattern, define a typed portable operation and its mobile runtime adapter, and add tests. Re-run the inventory and verify all new mobile packages and dependencies before updating their pins.
