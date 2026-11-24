package app.valenced.xposed.tweaks.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import app.valenced.xposed.tweaks.bridge.StereoRoutePolicy.Preference
import app.valenced.xposed.tweaks.bridge.StereoRoutePolicy.Path
import app.valenced.xposed.tweaks.bridge.StereoRoutePolicy.Inputs

class StereoRoutePolicyTest {
    private val dual=Inputs(true,false,true,true,true,false)
    @Test fun dualModeHeadphones(){
        assertEquals(Path.LE_COMMUNICATION,StereoRoutePolicy.choose(Preference.AUTO,dual))
        assertEquals(Path.A2DP_PHONE_MIC,StereoRoutePolicy.choose(Preference.A2DP,dual))
        assertEquals(Path.LE_COMMUNICATION,StereoRoutePolicy.choose(Preference.LE,dual))
    }
    @Test fun fallbackToA2dpWhenLeCannotCommunicate(){
        assertEquals(Path.A2DP_PHONE_MIC,StereoRoutePolicy.choose(Preference.AUTO,dual.copy(leCommunication=false)))
        assertEquals(Path.SYSTEM,StereoRoutePolicy.choose(Preference.LE,dual.copy(leCommunication=false)))
    }
    @Test fun leOnly(){
        assertEquals(Path.LE_COMMUNICATION,StereoRoutePolicy.choose(Preference.AUTO,dual.copy(a2dp=false)))
        assertEquals(Path.SYSTEM,StereoRoutePolicy.choose(Preference.A2DP,dual.copy(a2dp=false)))
    }
    @Test fun ordinaryRoutingIsPreserved(){
        for (p in Preference.entries){
            for (i in listOf(dual.copy(enabled=false),dual.copy(conflict=true),dual.copy(ordinaryMode=false),
                dual.copy(wired=true),dual.copy(a2dp=false,leCommunication=false))){
                assertEquals(Path.SYSTEM,StereoRoutePolicy.choose(p,i))
            }
        }
    }
}
