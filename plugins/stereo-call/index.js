(() => {
  const native=valenced.modules.native;
  let settings,active=false,armed=null,timer=null,status={label:"Nicht gestartet"},revision=0;
  let config={preferStereo:true,restoreOnDisconnect:true};
  const listeners=new Set();
  function publish(label,route){status={label,route:route||status.route};for(const f of listeners)f(++revision)}
  function route(){try{return native.callNativeMethodSync("vexcord.audio.route",[])||{}}catch{return {}}}
  function restore(){
    const old=armed;armed=null;
    if(old)try{Reflect.apply(old.original,old.receiver,old.args)}catch{}
  }
  function inspect(){
    const r=route();
    if(armed&&(!r.eligible||!config.preferStereo)&&config.restoreOnDisconnect)restore();
    publish(armed?"Stereo-Priorität angefordert":r.conflict?"Bluetooth Audio Fix zuerst deaktivieren":r.leAudio?"LE Audio · Systemrouting":r.wired?"Kabel/USB · Systemrouting":r.a2dp?"A2DP verfügbar · nächster Anruf":"Normales Android-Routing",r)
  }
  function SettingsComponent(){
    const {React,ReactNative:RN}=valenced.react;
    const [,refresh]=React.useState(0);
    React.useEffect(()=>{listeners.add(refresh);inspect();return()=>listeners.delete(refresh)},[]);
    const h=React.createElement,T=RN.Text,V=RN.View;
    const row=(title,detail,key)=>h(V,{style:{paddingVertical:12,flexDirection:"row",alignItems:"center",borderBottomWidth:1,borderColor:"#33303e"}},
      h(V,{style:{flex:1,paddingRight:12}},h(T,{style:{color:"#f1eaff",fontSize:15,fontWeight:"600"}},title),h(T,{style:{color:"#aea5be",fontSize:12,marginTop:4}},detail)),
      h(RN.Switch,{value:config[key],onValueChange:v=>{config={...config,[key]:v};settings?.set(config).catch(()=>{});if(!v&&key==="preferStereo")restore();inspect()}}));
    return h(RN.ScrollView,{style:{flex:1,backgroundColor:"#10111a"},contentContainerStyle:{padding:18}},
      h(T,{style:{color:"#c9b3ff",fontSize:24,fontWeight:"800"}},"Stereo Call"),
      h(T,{style:{color:"#bab3ca",marginTop:6}},"Musik und Voice · Vexcord Audio"),
      h(V,{style:{backgroundColor:"#1c2030",borderRadius:18,padding:16,marginVertical:18}},
        h(T,{style:{color:"#85e6ca",fontWeight:"600"}},status.label),
        h(T,{style:{color:"#bcb7cd",marginTop:8}}, "A2DP: "+(status.route?.a2dp?"verfügbar":"nicht erkannt")+"   ·   LE Audio: "+(status.route?.leAudio?"ja":"nein"))),
      row("Stereo bevorzugen","Nur bei klassischem Bluetooth-A2DP. Discord kann dabei das Telefonmikrofon statt des Headset-Mikrofons verwenden.","preferStereo"),
      row("Automatische Rückkehr","Bei verlorenem Audio-Routing die ursprüngliche Discord-Kommunikationsanforderung wieder ausführen.","restoreOnDisconnect"),
      h(RN.Pressable,{onPress:()=>{restore();publish("Standardrouting wiederhergestellt",route())},style:{padding:14,borderRadius:12,backgroundColor:"#302a48",marginTop:14}},h(T,{style:{color:"#e7dcff",textAlign:"center",fontWeight:"600"}},"Standardrouting für diesen Anruf")),
      h(T,{style:{color:"#aaa2b9",fontSize:12,marginTop:18}},"Kein erzwungener globaler Audiomodus. HFP und A2DP können hardwarebedingt nicht gleichzeitig in voller Stereoqualität mit Headset-Mikrofon laufen. Bei fehlendem Gesprächston Standardrouting wählen oder den Anruf neu verbinden.")
    )
  }
  return {default:vexcord.definePlugin({
    async start(api){
      const {plugin,cleanup}=api;
      active=true;cleanup(()=>{active=false;if(timer)clearInterval(timer);timer=null;restore();publish("Gestoppt",{})});
      settings=api.vexcord.storage.open("audio",{preferStereo:true,restoreOnDisconnect:true});
      config={...config,...await settings.get()};if(!active)return;
      let module;
      for(const name of ["NativeAudioManagerModule","RTNAudioManager"]){try{module=native.getNativeModule(name);if(module)break}catch{}}
      if(!module||typeof module.setCommunicationModeOn!=="function"||typeof module.setCommunicationModeOff!=="function"){publish("Audio-Hook nicht verfügbar",route());return}
      cleanup(valenced.patcher.instead(module,"setCommunicationModeOn",function(args,original){
        const r=route();
        if(!active||!config.preferStereo||!r.eligible)return Reflect.apply(original,this,args);
        armed={original,receiver:this,args:args.slice()};
        publish("Stereo-Priorität angefordert",r);
        // As in the existing RN Android audio compatibility ABI, this is a void method.
        return undefined;
      }));
      cleanup(valenced.patcher.before(module,"setCommunicationModeOff",args=>{armed=null;return args}));
      publish("Audio-Hook bereit",route());
      timer=setInterval(inspect,3000);
      nativeLoggingHook("[Vexcord Stereo] Communication hooks bound",1);
    },SettingsComponent
  })};
})()
