package app.valenced.xposed.manager

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject

/** Explicitly selected BitChord session only. No notification access, no
 * playback commands, no track history and no requests to other players. */
internal object BitChordSession {
    private const val PACKAGE="com.music.bitchord"
    private const val SERVICE="com.music.bitchord.playback.PlaybackService"
    private const val LEASE=25000L
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var snapshot="""{"status":"idle"}"""
    @Volatile private var lastRequest=0L
    private var browser:MediaBrowser?=null
    private var controller:MediaController?=null
    private var lastAttempt=0L
    private var callback:MediaController.Callback?=null
    private val expire=Runnable{
        if(SystemClock.elapsedRealtime()-lastRequest>=LEASE)disconnect("idle")
    }
    fun read(context:Context):String{
        lastRequest=SystemClock.elapsedRealtime()
        handler.post{
            handler.removeCallbacks(expire)
            handler.postDelayed(expire,LEASE)
            if(controller!=null)update()
            else if(browser==null&&SystemClock.elapsedRealtime()-lastAttempt>=5000L)connect(context.applicationContext)
        }
        return snapshot
    }
    fun stop(){
        lastRequest=0L
        handler.post{handler.removeCallbacks(expire);disconnect("idle")}
    }
    private fun connect(context:Context){
        lastAttempt=SystemClock.elapsedRealtime()
        snapshot="""{"status":"connecting"}"""
        try{
            val component=ComponentName(PACKAGE,SERVICE)
            val service=context.packageManager.getServiceInfo(component,0)
            require(service.exported&&service.packageName==PACKAGE)
            lateinit var candidate:MediaBrowser
            candidate=MediaBrowser(context,component,object:MediaBrowser.ConnectionCallback(){
                override fun onConnected(){
                    if(browser!==candidate)return
                    try{
                        val c=MediaController(context,candidate.sessionToken)
                        require(c.packageName==PACKAGE)
                        controller=c
                        val observer=object:MediaController.Callback(){
                            override fun onMetadataChanged(metadata:MediaMetadata?){update()}
                            override fun onPlaybackStateChanged(state:PlaybackState?){update()}
                            override fun onSessionDestroyed(){disconnect("session-ended")}
                        }
                        callback=observer
                        c.registerCallback(observer,handler)
                        update()
                    }catch(_:Exception){disconnect("unavailable")}
                }
                override fun onConnectionFailed(){if(browser===candidate)disconnect("connection-denied")}
                override fun onConnectionSuspended(){if(browser===candidate)disconnect("suspended")}
            },null)
            browser=candidate
            candidate.connect()
            handler.postDelayed({
                if(browser===candidate&&controller==null)disconnect("connection-timeout")
            },12000L)
        }catch(_:Exception){disconnect("unavailable")}
    }
    private fun disconnect(status:String){
        callback?.let{runCatching{controller?.unregisterCallback(it)}}
        callback=null;controller=null
        val b=browser;browser=null
        runCatching{b?.disconnect()}
        snapshot=JSONObject().put("status",status).toString()
    }
    private fun update(){
        val c=controller?:return
        try{
            val m=c.metadata
            val state=c.playbackState
            if(m==null||state==null){snapshot="""{"status":"connected","playing":false}""";return}
            fun text(key:String,max:Int)=m.getString(key).orEmpty().trim().take(max)
            val title=text(MediaMetadata.METADATA_KEY_TITLE,128).ifBlank{text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,128)}
            val artist=text(MediaMetadata.METADATA_KEY_ARTIST,128).ifBlank{text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,128)}
            val album=text(MediaMetadata.METADATA_KEY_ALBUM,128)
            val description=m.description
            val candidates=(listOf(MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                .map{ text(it,1800) }+listOf(description.iconUri?.toString().orEmpty().take(1800)))
                .filter{it.startsWith("https://")}.distinct().take(4)
            val art=candidates.firstOrNull().orEmpty()
            val mediaId=text(MediaMetadata.METADATA_KEY_MEDIA_ID,160).ifBlank{description.mediaId.orEmpty().take(160)}
            val mediaUri=text(MediaMetadata.METADATA_KEY_MEDIA_URI,1000).ifBlank{description.mediaUri?.toString().orEmpty().take(1000)}
            val playing=state.state==PlaybackState.STATE_PLAYING
            val duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceIn(0,24*60*60*1000L)
            val advance=if(playing&&state.lastPositionUpdateTime>0L)
                ((SystemClock.elapsedRealtime()-state.lastPositionUpdateTime).coerceAtLeast(0)*state.playbackSpeed).toLong() else 0L
            val position=(state.position.coerceAtLeast(0L)+advance).coerceIn(0L,if(duration>0)duration else 24*60*60*1000L)
            snapshot=JSONObject().put("status","connected").put("playing",playing).put("playbackState",state.state).put("title",title).put("artist",artist)
                .put("album",album).put("art",art).put("artCandidates",org.json.JSONArray(candidates))
                .put("mediaId",mediaId).put("mediaUri",mediaUri)
                .put("duration",duration).put("position",position).put("sampleTime",System.currentTimeMillis()).toString()
        }catch(_:Exception){disconnect("unavailable")}
    }
}
