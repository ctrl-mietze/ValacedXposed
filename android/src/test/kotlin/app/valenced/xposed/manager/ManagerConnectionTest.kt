package app.valenced.xposed.manager

import app.valenced.xposed.BuildConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagerConnectionTest {
    @Test fun onlyFreshCompatibleEnabledPeerIsOnline() {
        val peer=ManagerConnection.Peer(42,BuildConfig.VERSION_CODE,1000L,true,true,true)
        assertTrue(peer.online(1001L))
        assertTrue(peer.online(4500L))
        assertFalse(peer.online(4501L))
        assertFalse(peer.copy(version=0).online(1001L))
        assertFalse(peer.copy(enabled=false).online(1001L))
        assertFalse(peer.copy(pid=0).online(1001L))
        assertFalse(peer.online(999L))
    }
}
