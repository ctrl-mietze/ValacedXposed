import type { VexcordRoot } from '../lib/plugins/src/vexcord/contracts'
import type {
    PluginApiExtensionsOptions,
    PluginOptions,
    UnscopedInitPluginApi,
    UnscopedPluginApi,
    UnscopedPreInitPluginApi,
} from '@valenced/plugins/types'

declare global {
    /** Vexcord SDK v1 root. Available to normal external plugin bundles. */
    export const vexcord: VexcordRoot

    /**
     * Defines an entrypoint for a Valenced JS plugin.
     */
    export function plugin<O extends PluginApiExtensionsOptions>(
        options: PluginOptions<O>,
    ): PluginOptions<O>

    /**
     * The unscoped plugin API. **Use directly only when necessary.**
     *
     * You should be using the `unscoped` property of the `PluginApi` object instead.
     */
    export const valenced:
        | UnscopedPreInitPluginApi
        | UnscopedInitPluginApi
        | UnscopedPluginApi
}

export * from './globals'
