# Vexcord SDK v1

The SDK is a real, additive runtime API built on the maintained Android integration layer. It does not replace Valenced's existing compatibility interfaces.

## Entrypoint

External plugin bundles continue to use the existing format-1 manifest and JavaScript expression returning an object with a `default` property. The loader injects the original `valenced` and `plugin` arguments and adds `vexcord`.

Use the local [`@vexcord/types`](../sdk/) TypeScript package. It is part of the source tree, not an npm publication.

```ts
export default vexcord.definePlugin({
    async start({ vexcord: api }) {
        const settings = api.storage.open("settings", { enabled: true })
        const { enabled } = await settings.get()
        if (enabled) {
            api.messages.transformOutgoing(text =>
                text.replaceAll("[demo]", "Vexcord")
            )
        }
    },
})
```

Use existing Valenced build tooling to compile the plugin, or inspect the [minimal JS bundle example](../sdk/example/).

## API reference

| API | Purpose |
| --- | --- |
| `vexcord.name` | Product identity |
| `vexcord.apiVersion` | Stable SDK major version, currently 1 |
| `vexcord.capabilities` | Names of implemented API groups, not a live Metro binding guarantee |
| `vexcord.runtimeInfo()` | Read-only native product/version/platform metadata |
| `vexcord.definePlugin(options)` | Validates lifecycle callbacks and returns the options object |
| `api.vexcord.id` | Current plugin ID |
| `api.vexcord.modules.observe(keys, callback)` | Bounded, lazy structural Metro discovery; no forced module initialization |
| `api.vexcord.hooks.before(target, key, transform)` | Reversible argument hook; return the replacement argument array |
| `api.vexcord.hooks.after(target, key, transform)` | Reversible result hook; return the desired result |
| `api.vexcord.events.observe(name, callback)` | Observe a Flux event; the dispatcher receives its original payload |
| `api.vexcord.storage.open(name, defaults)` | Per-plugin JSON storage with get/set/replace/subscribe |
| `api.vexcord.messages.transformOutgoing(fn)` | Transform content in an existing send action |
| `api.vexcord.messages.transformEdit(fn)` | Transform content in an existing edit action |
| `api.vexcord.onCleanup(fn)` | Register an idempotent disposer |

Every registered scope hook and subscription is released at plugin stop. Methods are callable during start/stop only, not during preInit. Storage documents are named `vexcord-<name>.json` within the existing plugin storage directory; their names cannot contain traversal paths.

The message API recognizes actual structural Android action exports and supports both native message records and text-only wrappers. It preserves the original record and unrelated fields; it never sends a message on its own. A lazy module might not be available on every Discord build. Test the real target.

## Compatibility policy

- Keep `valenced.api`, existing plugin IDs, manifests and `@valenced/*` imports.
- Preserve `plugin(...)` and the original `valenced` loader argument.
- Keep existing native method names, with additive Vexcord aliases for first-party theme/compiler operations.
- Do not treat a plugin API as a security sandbox. Installed plugins execute in the Discord process. Plugin Lab simulations are a separate bounded diagnostic feature.

See [source contracts](../bundle/lib/plugins/src/vexcord/contracts.ts) and [runtime implementation](../bundle/lib/plugins/src/vexcord/runtime.ts).
