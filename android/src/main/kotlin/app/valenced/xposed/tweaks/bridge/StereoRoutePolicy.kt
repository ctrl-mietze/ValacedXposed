package app.valenced.xposed.tweaks.bridge

/** Portable decision table. LE capability is not equivalent to the active
 * route. Dual-mode headphones are valid A2DP targets, not exclusions. */
internal object StereoRoutePolicy {
    enum class Preference { AUTO, A2DP, LE }
    enum class Path { SYSTEM, A2DP_PHONE_MIC, LE_COMMUNICATION }
    data class Inputs(val enabled:Boolean,val conflict:Boolean,val ordinaryMode:Boolean,
        val a2dp:Boolean,val leCommunication:Boolean,val wired:Boolean)
    fun choose(p:Preference,i:Inputs):Path{
        if(!i.enabled||i.conflict||!i.ordinaryMode||i.wired)return Path.SYSTEM
        return when(p){
            Preference.AUTO->when{
                i.leCommunication->Path.LE_COMMUNICATION
                i.a2dp->Path.A2DP_PHONE_MIC
                else->Path.SYSTEM
            }
            Preference.A2DP->if(i.a2dp)Path.A2DP_PHONE_MIC else Path.SYSTEM
            Preference.LE->if(i.leCommunication)Path.LE_COMMUNICATION else Path.SYSTEM
        }
    }
}
