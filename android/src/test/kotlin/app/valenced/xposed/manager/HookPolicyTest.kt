package app.valenced.xposed.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookPolicyTest {
    @Test fun allCombinationsHavePredictablePriority() {
        assertEquals(HookMode.OFF, HookPolicy.resolve(false, false, false))
        assertEquals(HookMode.OFF, HookPolicy.resolve(false, true, true))
        assertEquals(HookMode.RECOVERY, HookPolicy.resolve(true, true, true))
        assertEquals(HookMode.RECOVERY, HookPolicy.resolve(true, false, true))
        assertEquals(HookMode.MINIMAL, HookPolicy.resolve(true, false, false))
        assertEquals(HookMode.FULL, HookPolicy.resolve(true, true, false))
    }

    @Test fun onlyFullModeInjectsJavascriptBundle() {
        assertFalse(HookPolicy.shouldInject(HookMode.OFF))
        assertFalse(HookPolicy.shouldInject(HookMode.RECOVERY))
        assertFalse(HookPolicy.shouldInject(HookMode.MINIMAL))
        assertTrue(HookPolicy.shouldInject(HookMode.FULL))
    }
}
