(() => {
  const native=valenced.modules.native;
  const CLIENT_ID="1521756469433991299",SOCKET="Vexcord:BitChord",PID=46229;
  let live=false,timer=null,store=null,busy=false,epoch=0,lastKey="",lastStart=0,hasActivity=false;
  let config={publish:true,cover:true,timestamps:true,album:true,link:true,showPaused:false};
  let view={status:"Nicht gestartet",playing:false};const watchers=new Set(),artCache=new Map();
  let revision=0,lastSentAt=0,lastAccount=null,lastReport="",lastActivity=null,lastCoverAttempt=0;
  let connection={discord:"waiting",media:"idle",publication:"idle",cover:"idle"};
  function report(){
    const summary=JSON.stringify(connection);
    try{native.callNativeMethodSync("vexcord.bitchord.status",[summary])}catch{}
    if(summary!==lastReport){lastReport=summary;nativeLoggingHook("[Vexcord BitChord] "+summary,1)}
  }
  function stage(patch){connection={...connection,...patch};report()}
  function currentUser(){
    try{const u=valenced.discord.flux.Stores.UserStore?.getCurrentUser?.();return typeof u?.id==="string"?u.id:null}catch{return null}
  }
  function observed(){
    try{
      const id=currentUser();if(!id)return null;
      const store=valenced.discord.flux.Stores.PresenceStore;
      if(typeof store?.getActivities!=="function")return null;
      const a=store.getActivities(id);
      return Array.isArray(a)?a.some(x=>x?.application_id===CLIENT_ID&&x?.details===lastActivity?.details&&x?.type===lastActivity?.type):null
    }catch{return null}
  }
  function dispatcher(){
    try{
      const d=valenced.discord.common.flux.Dispatcher;
      if(typeof d?.dispatch==="function")return d;
    }catch{}
    return null
  }
  function update(data){view=data;for(const f of watchers)f(++revision)}
  function text(v,n=128){return typeof v==="string"?v.replace(/[\x00-\x1f\x7f]/g," ").trim().slice(0,n):""}
  // RN's URLSearchParams implementation may lack keys()/iteration.
  // Parse the small supported public URL surface without that dependency.
  function artUrl(v){
    if(typeof v!=="string"||v.length>1800)return "";
    const match=v.match(/^https:\/\/([a-z0-9.-]+)(\/[^\s?#\\\\]*)(?:\?([^\s#]*))?$/i);
    if(!match)return "";
    const host=match[1].toLowerCase(),path=match[2],query=match[3]||"";
    if(!/^(?:[a-z0-9-]+\.)*(?:ytimg\.com|googleusercontent\.com|ggpht\.com|sndcdn\.com)$/.test(host))return "";
    if(/%(?:00|0a|0d|2f|5c)/i.test(path)||path.includes(".."))return "";
    if(query&&!query.split("&").every(part=>/^(?:sqp|rs|sz|w|h|width|height|quality|format)=[A-Za-z0-9_%.,=+~:-]{1,500}$/.test(part)))return "";
    return "https://"+host+path+(query?"?"+query:"")
  }
  function youtubeId(m){
    const uri=typeof m.mediaUri==="string"?m.mediaUri:"";
    const link=uri.match(/^https:\/\/(?:music\.youtube\.com|www\.youtube\.com|youtube\.com)\/watch\?(?:[^#\s]*&)?v=([A-Za-z0-9_-]{11})(?:&|#|$)/)||
      uri.match(/^https:\/\/youtu\.be\/([A-Za-z0-9_-]{11})(?:[?#]|$)/);
    if(link)return link[1];
    // BitChord's current MediaItem ID is used only for a recognized public
    // YouTube ID, not a file URI, session credential or SoundCloud identifier.
    if(/soundcloud|^file:|^content:/i.test(uri))return "";
    const id=typeof m.mediaId==="string"?m.mediaId:"";
    return (id.match(/^(?:(?:youtube|yt|ytmusic):)?([A-Za-z0-9_-]{11})$/i)||[])[1]||""
  }
  function trackUrl(m){
    const id=youtubeId(m);
    if(id)return "https://music.youtube.com/watch?v="+id;
    const v=typeof m.mediaUri==="string"?m.mediaUri:"";
    const match=v.match(/^https:\/\/soundcloud\.com\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+\/?(?:\?[^#]*)?$/);
    return match?v.split("?")[0]:""
  }
  function artwork(m){
    const urls=[m.art,...(Array.isArray(m.artCandidates)?m.artCandidates.slice(0,4):[])].map(artUrl).filter(Boolean);
    const id=youtubeId(m);
    if(id)urls.push("https://i.ytimg.com/vi/"+id+"/hqdefault.jpg");
    return [...new Set(urls)].slice(0,4)
  }
  function assetId(v){
    if(typeof v!=="string"||v.length>1000)return "";
    return /^(?:mp:[A-Za-z0-9_./%~+=:-]+|[0-9]{16,22}|[a-f0-9]{32})$/i.test(v)?v:""
  }
  function dispatch(activity){
    const d=dispatcher();if(!d)throw Error("dispatcher-unavailable");
    const result=d.dispatch({type:"LOCAL_ACTIVITY_UPDATE",activity,pid:PID,socketId:SOCKET});
    hasActivity=!!activity;lastActivity=activity;
    return result;
  }
  function clear(){
    if(hasActivity)try{dispatch(null)}catch{}
    lastKey="";lastStart=0;lastSentAt=0;lastCoverAttempt=0;
  }
  async function resolveCover(url){
    if(!url)return "";
    if(artCache.has(url))return artCache.get(url);
    const f=valenced.modules.finders;
    function utility(candidate,method){
      if(typeof candidate?.[method]==="function")return candidate;
      return typeof candidate?.default?.[method]==="function"?candidate.default:null
    }
    let assets;
    try{
      const [m]=valenced.discord.utils.modules.finders.lookupModuleWithImportedPath("utils/ApplicationAssetUtils.tsx");
      assets=utility(m,"fetchAssetIds")
    }catch{}
    if(!assets)try{
      const [m]=f.lookupModule(f.filters.withProps("getAssetIds","fetchAssetIds"),{returnNamespace:true});
      assets=utility(m,"fetchAssetIds")
    }catch{}
    let result="";
    if(assets)try{
      const ids=await assets.fetchAssetIds(CLIENT_ID,[url]);
      result=assetId(Array.isArray(ids)?ids[0]:"");
    }catch{}
    if(!result)try{
      // The installed Android REST facade exports getAPIBaseURL/get/post.
      const [candidate]=f.lookupModule(f.filters.withProps("getAPIBaseURL","get","post"),{returnNamespace:true});
      const rest=utility(candidate,"post");
      if(rest){
        const r=await rest.post({url:"/applications/"+CLIENT_ID+"/external-assets",body:{urls:[url]},oldFormErrors:true,rejectWithError:false});
        const item=Array.isArray(r?.body)?r.body[0]:undefined;
        if(r?.ok!==false&&typeof item?.external_asset_path==="string")result=assetId("mp:"+item.external_asset_path)
      }
    }catch{}
    if(result){if(artCache.size>=24)artCache.clear();artCache.set(url,result)}
    return result
  }
  async function resolveArtwork(urls){
    for(const url of urls){
      let timeout;
      try{
        const id=await Promise.race([resolveCover(url),new Promise(resolve=>{timeout=setTimeout(()=>resolve(""),4000)})]);
        if(id)return {id,url}
      }finally{if(timeout)clearTimeout(timeout)}
    }
    return {id:"",url:""}
  }
  async function poll(){
    if(!live||busy)return;busy=true;const generation=epoch;
    try{
      if(!config.publish){
        clear();stage({publication:"disabled",cover:"idle"});
        update({status:"Rich Presence deaktiviert",playing:false});return
      }
      // Use the current authenticated client session. No raw credential is
      // accessed, returned, stored or transmitted by this plugin.
      const d=dispatcher(),user=currentUser();
      stage({discord:d&&user?"ready":"waiting"});
      if(!d||!user){
        clear();stage({publication:"waiting"});
        update({status:"Warte auf angemeldeten Discord-Client",playing:false});return
      }
      if(lastAccount!==user){clear();lastAccount=user}
      let raw;
      try{raw=await native.callNativeMethod("vexcord.bitchord.session",[])}
      catch{
        if(!live||generation!==epoch)return;
        clear();stage({media:"bridge-error",publication:"idle"});
        update({status:"BitChord-Medienbridge nicht verfügbar",playing:false});return
      }
      if(!live||generation!==epoch)return;
      let m;
      try{m=typeof raw==="string"?JSON.parse(raw):raw||{}}catch{
        clear();stage({media:"bridge-error",publication:"idle"});return
      }
      const title=text(m.title),artist=text(m.artist),album=text(m.album),covers=artwork(m),cover=covers[0]||"",link=trackUrl(m);
      const playing=m.status==="connected"&&m.playing===true&&!!title;
      const paused=m.status==="connected"&&!playing&&!!title&&(m.playbackState===2||m.playbackState===undefined);
      const media=m.status==="connected"?(playing?"playing":paused?"paused":"idle"):
        ["connecting","connection-denied","connection-timeout","suspended","session-ended","disabled","unavailable","idle"].includes(m.status)?m.status:"unavailable";
      if(m.status==="connected"&&Number.isFinite(m.sampleTime)&&Math.abs(Date.now()-m.sampleTime)>25000){
        clear();stage({media:"unavailable",publication:"idle"});update({status:"Warte auf aktuelle BitChord-Daten",playing:false});return
      }
      stage({media});
      const position=Number.isFinite(m.position)?Math.max(0,m.position):0;
      const duration=Number.isFinite(m.duration)?Math.max(0,m.duration):0;
      const stamp=Number.isFinite(m.sampleTime)&&Math.abs(Date.now()-m.sampleTime)<15000?m.sampleTime:Date.now();
      update({status:playing?"BitChord verbunden · Wiedergabe":paused?"BitChord verbunden · pausiert":"BitChord: "+media,playing,title,artist,album,cover,position,duration});
      if(!playing&&!(paused&&config.showPaused)){clear();stage({publication:"idle",cover:"idle"});return}
      const started=stamp-position;
      const key=JSON.stringify([title,artist,album,covers,link,playing,config]);
      const confirmed=observed();
      const now=Date.now();
      // Do not flood the dispatcher if Discord is offline or suppresses
      // activity sharing. Reconcile at most once per 30 seconds.
      if(key===lastKey&&(!config.timestamps||!playing||Math.abs(started-lastStart)<8000)&&
        (confirmed!==false||now-lastSentAt<30000)&&
        (!config.cover||!covers.length||connection.cover==="resolved"||now-lastCoverAttempt<60000)){
        stage({publication:confirmed===true?"observed":"submitted"});
        update({...view,status:confirmed===true?"Discord-Aktivität erkannt":"Aktivität an Discord übergeben"});return
      }
      let image="";
      // Publish the text immediately, then enrich the card asynchronously.
      const activity={name:"BitChord",type:playing?2:0,flags:0,application_id:CLIENT_ID,status_display_type:1,
        details:title,state:paused?text("Pausiert · "+(artist||"BitChord")):artist||"Musik hören"};
      if(playing&&config.album&&album)activity.state=text((artist?artist+" · ":"")+album);
      if(playing&&config.timestamps){activity.timestamps={start:Math.max(0,Math.round(started))};if(duration>0&&duration>=position)activity.timestamps.end=Math.round(started+duration)}
      if(config.link&&link){activity.buttons=["Song öffnen"];activity.metadata={button_urls:[link]}}
      const cachedCover=covers.find(url=>artCache.has(url));
      if(config.cover&&cachedCover){
        image=artCache.get(cachedCover);activity.assets={large_image:image,large_text:text(album||title)}
      }
      await Promise.resolve(dispatch(activity));
      if(!live||generation!==epoch||!config.publish)return;
      lastKey=key;lastStart=started;lastSentAt=Date.now();
      stage({publication:observed()===true?"observed":"submitted",cover:image?"resolved":config.cover&&cover?"pending":"idle"});
      update({...view,status:"Aktivität an Discord übergeben"});
      if(config.cover&&covers.length&&!image){
        lastCoverAttempt=Date.now();
        const found=await resolveArtwork(covers);
        if(!live||generation!==epoch||!config.publish)return;
        // Never attach an older song's cover after a track change/pause.
        let latest;
        try{const raw=await native.callNativeMethod("vexcord.bitchord.session",[]);latest=typeof raw==="string"?JSON.parse(raw):raw}catch{}
        if(!live||generation!==epoch||!config.publish)return;
        if(!latest||latest.status!=="connected"||text(latest.title)!==title||
            text(latest.artist)!==artist||text(latest.album)!==album||
            JSON.stringify(artwork(latest))!==JSON.stringify(covers)||
            (latest.playing===true)!==playing){lastKey="";return}
        if(found.id){
          await Promise.resolve(dispatch({...activity,assets:{large_image:found.id,large_text:text(album||title)}}));
          stage({cover:"resolved",publication:observed()===true?"observed":"submitted"})
        }else stage({cover:"unavailable"});
      }
      update({...view,status:connection.publication==="observed"?"Discord-Aktivität erkannt":"Aktivität an Discord übergeben"});
    }catch{
      if(live&&generation===epoch){
        stage({publication:"error"});
        update({...view,status:"Discord konnte die Aktivität nicht übernehmen"})
      }
    }finally{busy=false}
  }
  function change(k,v){config={...config,[k]:v};epoch++;lastKey="";store?.set(config).catch(()=>{});if(k==="publish"&&!v){clear();native.callNativeMethod("vexcord.bitchord.stop",[]).catch(()=>{})}poll();for(const f of watchers)f(++revision)}
  function SettingsComponent(){
    const {React,ReactNative:RN}=valenced.react;
    const [,refresh]=React.useState(0);
    React.useEffect(()=>{watchers.add(refresh);poll();return()=>watchers.delete(refresh)},[]);
    const h=React.createElement,T=RN.Text,V=RN.View;
    const time=x=>Math.floor((x||0)/60000)+":"+String(Math.floor((x||0)/1000)%60).padStart(2,"0");
    const title=view.title||"BitChord",artist=view.artist||"Keine Wiedergabe";
    const row=(label,detail,k)=>h(V,{style:{flexDirection:"row",alignItems:"center",paddingVertical:14,borderBottomWidth:1,borderColor:"#302d3b"}},
      h(V,{style:{flex:1,paddingRight:12}},h(T,{style:{color:"#f2edfc",fontSize:15,fontWeight:"600"}},label),h(T,{style:{fontSize:12,color:"#a9a1b9",marginTop:3}},detail)),h(RN.Switch,{value:config[k],onValueChange:v=>change(k,v)}));
    return h(RN.ScrollView,{style:{flex:1,backgroundColor:"#0f1018"},contentContainerStyle:{padding:18}},
      h(T,{style:{fontSize:25,fontWeight:"800",color:"#d6c1ff"}},"BitChord Presence"),
      h(T,{style:{color:"#9e96b2",fontSize:13,marginTop:4}},"Vexcord · Listening activity"),
      h(V,{style:{marginVertical:18,padding:16,borderRadius:20,borderWidth:1,borderColor:"#554267",backgroundColor:"#201a2c"}},
        h(T,{style:{color:"#c5a9f8",fontSize:11,fontWeight:"700",letterSpacing:1.2,marginBottom:15}},"HÖRT BITCHORD"),
        h(V,{style:{flexDirection:"row",alignItems:"center"}},
          view.cover&&config.cover?h(RN.Image,{source:{uri:view.cover},style:{width:82,height:82,borderRadius:13,backgroundColor:"#30263e"}}):h(V,{style:{width:82,height:82,borderRadius:13,backgroundColor:"#47315e",alignItems:"center",justifyContent:"center"}},h(T,{style:{color:"#d8baff",fontSize:34,fontWeight:"bold"}},"♫")),
          h(V,{style:{flex:1,marginLeft:14}},h(T,{numberOfLines:2,style:{color:"#f9f5ff",fontWeight:"700",fontSize:18}},title),
            h(T,{numberOfLines:2,style:{color:"#d5c8e4",marginTop:5}},artist),
            config.album&&view.album?h(T,{numberOfLines:1,style:{color:"#aaa0b8",fontSize:11,marginTop:4}},view.album):null)),
        config.timestamps&&view.duration?h(V,{style:{marginTop:16}},h(V,{style:{height:5,borderRadius:6,overflow:"hidden",backgroundColor:"#493b58"}},h(V,{style:{height:5,backgroundColor:"#d6a5ff",width:Math.round(Math.min(1,(view.position||0)/view.duration)*100)+"%"}})),
          h(V,{style:{flexDirection:"row",justifyContent:"space-between",marginTop:7}},h(T,{style:{color:"#bbb1cf",fontSize:11}},time(view.position)),h(T,{style:{color:"#bbb1cf",fontSize:11}},time(view.duration)))):null,
        h(T,{style:{fontSize:12,color:view.playing?"#91e6c5":"#b9aecb",marginTop:14}},view.status)),
      h(V,{style:{padding:14,borderRadius:14,backgroundColor:"#1b1d2c",marginBottom:8}},
        h(T,{style:{color:"#d6c1ff",fontWeight:"700"}},"Automatische Verbindung"),
        ...[["Discord",connection.discord],["BitChord",connection.media],["Aktivität",connection.publication],["Cover",connection.cover]].map(([k,v])=>h(T,{key:k,style:{color:"#bcb4ce",fontSize:12,marginTop:5}},k+" · "+v))),
      row("Rich Presence aktiv","Nutzt automatisch deinen angemeldeten Discord-Client.","publish"),
      row("Pausierte Musik anzeigen","Zeigt bei pausierter Wiedergabe den letzten aktuellen Titel mit Pausiert-Hinweis.","showPaused"),
      row("Albumcover","Öffentliche Cover-URL zur Discord-Aktivitätsgrafik auflösen.","cover"),
      row("Zeitfortschritt","Position und Endzeit aus der BitChord-Mediensitzung.","timestamps"),
      row("Album anzeigen","Album zusätzlich zum Künstler anzeigen.","album"),
      row("Song-Link","Bei einem vorhandenen öffentlichen Titel-Link eine Schaltfläche hinzufügen.","link"),
      h(T,{style:{color:"#a9a1b9",fontSize:12,marginTop:16}},"Die Aktivität wird über dein Discord-Profil veröffentlicht. Andere Rich-Presence-Plugins werden nicht überschrieben oder gelöscht. Die Profilfreigabe in Discord bleibt maßgeblich. Eine lokal erkannte Aktivität ist kein Nachweis der Anzeige bei anderen Nutzern.")
    );
  }
  return {default:vexcord.definePlugin({
    async start(api){
      live=true;epoch++;
      api.cleanup(()=>{live=false;epoch++;if(timer)clearInterval(timer);timer=null;clear();native.callNativeMethod("vexcord.bitchord.stop",[]).catch(()=>{});artCache.clear();stage({discord:"waiting",media:"idle",publication:"stopped",cover:"idle"});update({status:"Gestoppt",playing:false})});
      store=api.vexcord.storage.open("presence",config);
      config={...config,...await store.get()};if(!live)return;
      try{
        const events=valenced.discord.flux;
        for(const event of ["CONNECTION_OPEN","RESUMED"]){
          if(typeof events.onFluxEventDispatched==="function")api.cleanup(events.onFluxEventDispatched(event,payload=>{
            lastKey="";return payload;
          }))
        }
      }catch{}
      stage({discord:"waiting",media:"idle",publication:"waiting",cover:"idle"});
      timer=setInterval(()=>{void poll()},5000);void poll();
      nativeLoggingHook("[Vexcord BitChord] Presence lifecycle started",1)
    },SettingsComponent
  })};
})()
