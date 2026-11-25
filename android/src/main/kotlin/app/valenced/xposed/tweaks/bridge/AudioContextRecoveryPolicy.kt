package app.valenced.xposed.tweaks.bridge

/** Exact predicate for the known Android framework crash; other exceptions propagate. */
internal object AudioContextRecoveryPolicy {
    fun isKnownCrash(error: Throwable): Boolean =
        error is NullPointerException &&
            error.message?.contains("getOpPackageName") == true &&
            error.stackTrace.firstOrNull()?.let {
                it.className == "android.media.AudioManager" && it.methodName == "setMode"
            } == true
}
