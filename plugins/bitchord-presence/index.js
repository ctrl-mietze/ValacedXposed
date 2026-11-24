(() => {
  const native=valenced.modules.native;
  const CLIENT_ID="1521756469433991299",SOCKET="Vexcord:BitChord",PID=46229;
  let live=false,timer=null,store=null,busy=false,epoch=0,lastKey="",lastStart=0,hasActivity=false;
  let config={publish:true,cover:true,timestamps:true,album:true,link:true};
  let view={status:"Nicht gestartet",playing:false};const watchers=new Set(),artCache=new Map();
  let revision=0;
  function update(data){view=data;for(const f of watchers)f(++revision)}
  function text(v,n=128){return typeof v==="string"?v.replace(/[\x00-\x1f\x7f]/g," ").trim().slice(0,n):""}
  function artUrl(v){
    try{const u=new URL(v);if(u.protocol!=="https:"||u.username||u.password||u.port||u.hash||u.href.length>1800)return "";
      if([...u.searchParams.keys()].some(k=>!["sqp","rs","sz","w","h"].includes(k)))return "";
      const host=u.hostname;return /^(?:[a-z0-9-]+\.)*(?:ytimg\.com|googleusercontent\.com|ggpht\.com|sndcdn\.com)$/.test(host)?u.href:""
    }catch{return ""}
  }
  function trackUrl(v){
    try{const u=new URL(v);if(u.protocol!=="https:"||u.username||u.password||u.port)return "";
      if(["music.youtube.com","www.youtube.com","youtube.com"].includes(u.hostname)&&u.pathname==="/watch"&&/^[\w-]{11}$/.test(u.searchParams.get("v")||""))return "https://music.youtube.com/watch?v="+u.searchParams.get("v");
      if(u.hostname==="youtu.be"&&/^\/[\w-]{11}$/.test(u.pathname))return "https://music.youtube.com/watch?v="+u.pathname.slice(1);
      if(u.hostname==="soundcloud.com"&&/^\/[\w-]+\/[\w-]+\/?$/.test(u.pathname))return "https://soundcloud.com"+u.pathname;
    }catch{}return ""
  }
  function dispatch(activity){
    const dispatcher=valenced.discord.common.flux.Dispatcher;
    dispatcher.dispatch({type:"LOCAL_ACTIVITY_UPDATE",activity,pid:PID,socketId:SOCKET});
    hasActivity=!!activity;
  }
  function clear(){if(hasActivity)try{dispatch(null)}catch{}lastKey="";lastStart=0}
  async function resolveCover(url){
    if(!url)return "";
    if(artCache.has(url))return artCache.get(url);
    try{
      const f=valenced.modules.finders;
      const [rest]=f.lookupModule(f.filters.withProps("get","post","put","patch","delete"));
      if(!rest||typeof rest.post!=="function")return "";
      // Same client-side external asset resolver used by Discord's activity API.
      // Only the selected public image URL is sent; no credentials are read.
      const r=await rest.post({url:"/applications/"+CLIENT_ID+"/external-assets",body:{urls:[url]},oldFormErrors:true,rejectWithError:false});
      const a=Array.isArray(r?.body)?r.body[0]:undefined;
      if(r?.ok===false||!a||typeof a.external_asset_path!=="string"||a.external_asset_path.length>1000)return "";
      const result="mp:"+a.external_asset_path;
      if(artCache.size>=24)artCache.clear();artCache.set(url,result);return result
    }catch{return ""}
  }
  async function poll(){
    if(!live||busy)return;busy=true;const generation=epoch;
    try{
      if(!config.publish){clear();update({status:"Rich Presence pausiert",playing:false});return}
      const raw=await native.callNativeMethod("vexcord.bitchord.session",[]);
      if(!live||generation!==epoch)return;
      const m=typeof raw==="string"?JSON.parse(raw):raw||{};
      const title=text(m.title),artist=text(m.artist),album=text(m.album),cover=artUrl(m.art),link=trackUrl(m.mediaUri);
      const playing=m.status==="connected"&&m.playing===true&&!!title;
      const position=Number.isFinite(m.position)?Math.max(0,m.position):0;
      const duration=Number.isFinite(m.duration)?Math.max(0,m.duration):0;
      const stamp=Number.isFinite(m.sampleTime)&&Math.abs(Date.now()-m.sampleTime)<15000?m.sampleTime:Date.now();
      update({status:m.status==="connected"?(playing?"BitChord verbunden · Wiedergabe":"BitChord verbunden · pausiert"):m.status||"Warte auf BitChord",playing,title,artist,album,cover,position,duration});
      if(!playing){clear();return}
      const started=stamp-position;
      const key=JSON.stringify([title,artist,album,cover,link,config]);
      if(key===lastKey&&(!config.timestamps||Math.abs(started-lastStart)<8000))return;
      const image=config.cover?await resolveCover(cover):"";
      if(!live||generation!==epoch||!config.publish)return;
      const activity={name:"BitChord",type:2,flags:0,application_id:CLIENT_ID,status_display_type:1,
        details:title,state:artist||"Musik hören"};
      if(config.album&&album)activity.state=text((artist?artist+" · ":"")+album);
      if(image)activity.assets={large_image:image,large_text:text(album||title)};
      if(config.timestamps){activity.timestamps={start:Math.max(0,Math.round(started))};if(duration>0&&duration>=position)activity.timestamps.end=Math.round(started+duration)}
      if(config.link&&link){activity.buttons=["Song öffnen"];activity.metadata={button_urls:[link]}}
      dispatch(activity);lastKey=key;lastStart=started;
      update({...view,status:image?"Rich Presence aktiv · Cover":"Rich Presence aktiv"});
    }catch{if(live){clear();update({status:"BitChord-Bridge nicht verfügbar",playing:false})}}finally{busy=false}
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
      row("Rich Presence aktiv","Nur der aktuelle BitChord-Titel; Pause/Ende entfernt diese Aktivität.","publish"),
      row("Albumcover","Öffentliche Cover-URL zur Discord-Aktivitätsgrafik auflösen.","cover"),
      row("Zeitfortschritt","Position und Endzeit aus der BitChord-Mediensitzung.","timestamps"),
      row("Album anzeigen","Album zusätzlich zum Künstler anzeigen.","album"),
      row("Song-Link","Bei einem vorhandenen öffentlichen Titel-Link eine Schaltfläche hinzufügen.","link"),
      h(T,{style:{color:"#a9a1b9",fontSize:12,marginTop:16}},"Die Aktivität wird über dein Discord-Profil veröffentlicht. Andere Rich-Presence-Plugins werden nicht überschrieben oder gelöscht. Kein Zugriff auf Benachrichtigungen oder Discord-Tokens.")
    );
  }
  return {default:vexcord.definePlugin({
    async start(api){
      live=true;epoch++;
      api.cleanup(()=>{live=false;epoch++;if(timer)clearInterval(timer);timer=null;clear();native.callNativeMethod("vexcord.bitchord.stop",[]).catch(()=>{});artCache.clear();update({status:"Gestoppt",playing:false})});
      store=api.vexcord.storage.open("presence",config);
      config={...config,...await store.get()};if(!live)return;
      timer=setInterval(()=>{void poll()},5000);void poll();
      nativeLoggingHook("[Vexcord BitChord] Presence lifecycle started",1)
    },SettingsComponent
  })};
})()
