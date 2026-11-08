package app.valenced.xposed.manager

/** Pure, independently testable policy for the hook bootstrap. */
internal enum class HookMode {
    OFF,
    RECOVERY,
    MINIMAL,
    FULL,
}

internal object HookPolicy {
    fun resolve(hookEnabled: Boolean, javascriptEnabled: Boolean, safeMode: Boolean): HookMode =
        when {
            !hookEnabled -> HookMode.OFF
            safeMode -> HookMode.RECOVERY
            !javascriptEnabled -> HookMode.MINIMAL
            else -> HookMode.FULL
        }

    fun shouldInject(mode: HookMode): Boolean = mode == HookMode.FULL
}
