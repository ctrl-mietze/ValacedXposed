package app.valenced.xposed.tweaks.media

import android.os.SystemClock

/** Ephemeral coordination only: no audio samples, media metadata or account data. */
internal object MediaRouteLease {
    val packages = setOf("com.music.bitchord", "com.spotify.music", "com.aspiro.tidal",
        "com.google.android.apps.youtube.music", "com.google.android.youtube",
        "app.morphe.android.youtube", "app.morphe.android.apps.youtube.music")
    private const val LEASE_MS = 9000L
    private data class State(val enabled:Boolean, val expires:Long)
    @Volatile private var state = State(false, 0L)
    @Synchronized fun publish(enabled:Boolean) {
        state = State(enabled, if (enabled) SystemClock.elapsedRealtime() + LEASE_MS else 0L)
    }
    fun enabled():Boolean = state.let { it.enabled && SystemClock.elapsedRealtime() < it.expires }
    private data class Report(val at:Long, val requested:Int, val verified:Int, val type:Int)
    private val reports = mutableMapOf<String, Report>()
    @Synchronized fun report(pkg:String, requested:Int, verified:Int, type:Int) {
        if(pkg !in packages) return
        reports[pkg] = Report(SystemClock.elapsedRealtime(), requested.coerceIn(0,128),
            verified.coerceIn(0,128), type)
    }
    @Synchronized fun status():String {
        val now = SystemClock.elapsedRealtime()
        val json = org.json.JSONObject()
        for ((pkg,r) in reports) if (now-r.at < 12000L)
            json.put(pkg, org.json.JSONObject().put("requested",r.requested)
                .put("verified",r.verified).put("type",r.type))
        return json.toString()
    }
}
