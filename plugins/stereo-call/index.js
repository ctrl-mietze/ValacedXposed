(() => {
  const native=valenced.modules.native;
  let settings,active=false,timer=null,revision=0,status={},generation=0;
  let config={preferStereo:true,routePreference:"auto",mediaRouting:true};
  const listeners=new Set();
  function update(s){status=s||{};for(const f of listeners)try{f(++revision)}catch{}}
  function inspect(){if(!active)return;try{update(native.callNativeMethodSync("vexcord.audio.route",[]))}catch{update({note:"backend-unavailable"})}}
  function configure(){
    if(!active)return;
    try{update(native.callNativeMethodSync("vexcord.audio.configure",[!!config.preferStereo,config.routePreference,!!config.mediaRouting]))}
    catch{update({note:"backend-unavailable"})}
  }
  function set(p){if(!active)return;config={...config,...p};settings?.set(config).catch(()=>{});configure()}
  function SettingsComponent(){
    const {React,ReactNative:RN}=valenced.react;
    const [,refresh]=React.useState(0);
    React.useEffect(()=>{listeners.add(refresh);inspect();return()=>listeners.delete(refresh)},[]);
    const h=React.createElement,T=RN.Text,V=RN.View;
    const label=status.note==="a2dp-native-trial-unverified"?"A2DP-Nativversuch · Sprachroute noch unbestätigt":status.note==="a2dp-track-verified"?"Medienausgang + Telefonmikrofon bestätigt":status.note==="split-not-verified"?"Medienroute nicht bestätigt · Standardrouting":status.note==="split-le-fallback"?"LE-Rückfall angefordert":status.note==="le-selected"?"LE Audio ausgewählt":status.note==="a2dp-sco-suppressed"||status.note==="a2dp-mode-request"?"A2DP-Priorität · Route testen":status.note==="backend-unavailable"?"Vexcord-Update erforderlich":status.note==="le-requested"?"LE Audio angefordert":status.note==="disabled"?"Standardrouting · Stereo deaktiviert":status.note==="idle"?"Bereit · nächster Anruf":String(status.note||"Bereit");
    const choices=[["a2dp_native","A2DP · native Audio-Engine (Test)","Experimenteller Medienmodus für OpenSL ES; Sprachroute muss im Anruf geprüft werden"],["auto","Automatisch","LE Audio bevorzugen, sonst A2DP"],["a2dp","Medienpfad + Telefonmikrofon","Getrennte Java-AudioTracks; bei fehlender Bestätigung zurück zu LE/System"],["le","LE Audio","Nur echte LE-Kommunikationsgeräte"]];
    let apps={};try{apps=JSON.parse(status.mediaApps||"{}")}catch{}
    const supported=[["com.music.bitchord","BitChord"],["com.spotify.music","Spotify"],["com.aspiro.tidal","TIDAL"],["com.google.android.apps.youtube.music","YouTube Music"],["com.google.android.youtube","YouTube"],["app.morphe.android.youtube","Morphe YouTube"],["app.morphe.android.apps.youtube.music","Morphe Music"]];
    return h(RN.ScrollView,{style:{flex:1,backgroundColor:"#10111a"},contentContainerStyle:{padding:18}},
      h(T,{style:{color:"#c9b3ff",fontSize:24,fontWeight:"800"}},"Stereo Call 1.4"),
      h(T,{style:{color:"#bab3ca",marginTop:6}},"Native Android-Audio-Engine"),
      h(V,{style:{backgroundColor:"#1c2030",borderRadius:18,padding:16,marginVertical:18}},
        h(T,{style:{color:"#85e6ca",fontWeight:"600"}},label),
        h(T,{style:{color:status.contextGuard?"#85e6ca":"#d9ae91",marginTop:8}},status.contextGuard?"Startschutz aktiv · Reparaturen: "+(status.contextRepairs||0):"Startschutz benötigt Vexcord 1.0.35+"),
        h(T,{style:{color:"#bcb7cd",marginTop:8}},"Native Hooks: "+(status.backend||0)+" · Aufrufe: "+(status.hookHits||0)),
        h(T,{style:{color:"#bcb7cd",marginTop:5}},"A2DP: "+(status.a2dp?"ja":"nein")+" · LE-Kommunikation: "+(status.leAudio?"ja":"nein")),
        h(T,{style:{color:"#bcb7cd",marginTop:5}},"Android-Modus: "+String(status.mode??"?")+" · Kommunikationsgerät: "+String(status.communicationType??"?")),
        h(T,{style:{color:"#bcb7cd",marginTop:5}},"Discord Call-Hook: "+(status.discordCallHook?"gebunden":"nicht gefunden")),
        h(T,{style:{color:"#bcb7cd",marginTop:5}},"Java-Ausgang: "+(status.javaOutputVerified||0)+"/"+(status.javaOutputRequests||0)+" · Mikrofon: "+(status.javaInputVerified||0)+"/"+(status.javaInputRequests||0))),
      h(V,{style:{paddingVertical:12,flexDirection:"row",alignItems:"center"}},
        h(T,{style:{color:"#f1eaff",flex:1,fontSize:16}},"Stereo-Priorität"),
        h(RN.Switch,{value:!!config.preferStereo,onValueChange:v=>set({preferStereo:v})})),
      h(V,{style:{paddingVertical:12,flexDirection:"row",alignItems:"center"}},
        h(T,{style:{color:"#f1eaff",flex:1,fontSize:16}},"Musik-Apps separat routen"),
        h(RN.Switch,{value:!!config.mediaRouting,onValueChange:v=>set({mediaRouting:v})})),
      h(T,{style:{color:"#bab3ca",marginBottom:8}},"LSPosed: gewünschte Player in den Modulbereich aufnehmen und neu starten."),
      ...supported.map(([pkg,name])=>h(V,{key:pkg,style:{paddingVertical:6,flexDirection:"row"}},
        h(T,{style:{color:"#f1eaff",flex:1}},name),
        h(T,{style:{color:"#bab3ca"}},apps[pkg]?(apps[pkg].verified+"/"+apps[pkg].requested+" A2DP bestätigt"):"Kein aktiver Hook-Bericht"))),
      ...choices.map(([key,name,sub])=>h(RN.Pressable,{key,onPress:()=>set({routePreference:key}),style:{padding:14,borderRadius:12,marginVertical:5,backgroundColor:config.routePreference===key?"#41335e":"#202234"}},
        h(T,{style:{color:"#f1eaff",fontSize:15,fontWeight:"600"}},name),
        h(T,{style:{color:"#bcb7cd",fontSize:12,marginTop:4}},sub))),
      h(RN.Pressable,{onPress:()=>set({preferStereo:false,mediaRouting:false,routePreference:"auto"}),style:{padding:14,borderRadius:12,backgroundColor:"#302a48",marginTop:14}},
        h(T,{style:{color:"#e7dcff",textAlign:"center",fontWeight:"600"}},"Standardrouting wiederherstellen")),
      h(T,{style:{color:"#aaa2b9",fontSize:12,marginTop:18}},"Nach Profilwechsel den Discord-Anruf neu verbinden. LE wählt den echten Kommunikationsausgang. Der Medienpfad wird nur bestätigt, wenn die Java-Ausgabe und das Telefonmikrofon tatsächlich auf den gewünschten Geräten liegen. Der native Testmodus bleibt ausdrücklich unbestätigt. Musik-Apps melden die tatsächliche AudioTrack-Route; Android kann eine parallele A2DP- und LE-Route ablehnen. Keine systemweiten Hooks.")
    )
  }
  return {default:vexcord.definePlugin({
    async start(api){
      const run=++generation;active=true;
      api.cleanup(()=>{
        if(run!==generation)return;
        active=false;++generation;
        if(timer!==null)clearInterval(timer);timer=null;
        try{update(native.callNativeMethodSync("vexcord.audio.configure",[false,"auto",false]))}catch{update({note:"backend-unavailable"})}
      });
      settings=api.vexcord.storage.open("audio",{preferStereo:true,routePreference:"auto",mediaRouting:true});
      let loaded={};try{loaded=await settings.get()}catch{}
      if(!active||run!==generation)return;
      config={preferStereo:true,routePreference:"auto",mediaRouting:true,...loaded};
      if(!["auto","a2dp","a2dp_native","le"].includes(config.routePreference))config.routePreference="auto";
      configure();timer=setInterval(inspect,3000);
      nativeLoggingHook("[Vexcord Stereo] Native controller configured; backend="+(status.backend||0),1);
    },SettingsComponent
  })};
})()
