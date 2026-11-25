package app.valenced.xposed.tweaks.bridge

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AudioContextRecoveryPolicyTest {
    private fun error(type: Throwable, owner: String, method: String): Throwable =
        type.apply { stackTrace = arrayOf(StackTraceElement(owner, method, "AudioManager.java", 3540)) }

    @Test fun exactSamsungFrameworkCrashCanBeRecovered() {
        assertTrue(AudioContextRecoveryPolicy.isKnownCrash(error(
            NullPointerException("Attempt to invoke virtual method Context.getOpPackageName() on a null object reference"),
            "android.media.AudioManager", "setMode")))
    }

    @Test fun unrelatedFailuresAreNotSwallowed() {
        assertFalse(AudioContextRecoveryPolicy.isKnownCrash(error(
            NullPointerException("getOpPackageName"), "android.media.AudioManager", "setSpeakerphoneOn")))
        assertFalse(AudioContextRecoveryPolicy.isKnownCrash(error(
            NullPointerException("getOpPackageName"), "com.discord.audio.AudioManagerModule", "setMode")))
        assertFalse(AudioContextRecoveryPolicy.isKnownCrash(error(
            SecurityException("getOpPackageName"), "android.media.AudioManager", "setMode")))
        assertFalse(AudioContextRecoveryPolicy.isKnownCrash(error(
            NullPointerException("unrelated"), "android.media.AudioManager", "setMode")))
    }
}
