(() => {
  // GPL-3.0. Inspired by Better Volume for Valenced (Axonyum), itself
  // based on Zerthox's BetterVolume. See NOTICE for sources and attribution.
  const LIMIT=500, STOCK=200, DEFAULT="default", STREAM="stream";
  const amp=p=>p<=0?0:p<100?100*Math.pow(p/100,2.8):100*Math.pow(10,6*(p/100-1)/20);
  const percent=a=>a<=0?0:a<100?100*Math.pow(a/100,1/2.8):100*(1+20*Math.log10(a/100)/6);
  let active=false, actions=null, saved={}, storage, dispatcher, timer, applying=false;
  let profileReady=false, pendingReady=false, streamReady=false, conversion=null, i18n=null;
  const listeners=new Set();
  const notify=()=>{for(const fn of listeners)fn()};
  const log=s=>{try{nativeLoggingHook("[Vexcord Volume Maxer] "+s,1)}catch{}};
  const bounded=p=>Math.min(LIMIT,Math.max(0,Number(p)||0));
  const toAmp=p=>{const n=bounded(p);if(conversion){try{const v=conversion.perceptualToAmplitude(n);if(Number.isFinite(v)&&(n<=STOCK||v>amp(STOCK)))return v}catch{}}return amp(n)};
  const toPercent=a=>{if(conversion){try{const v=conversion.amplitudeToPerceptual(a);if(Number.isFinite(v)&&(a<=amp(STOCK)||v>STOCK))return bounded(v)}catch{}}return bounded(percent(a))};
  const validId=id=>typeof id==="string"&&/^\d{15,22}$/.test(id);
  const validContext=c=>c===DEFAULT||c===STREAM;
  const keyFor=(c,id)=>c+":"+id;
  function store(){try{return valenced.discord.flux.Stores.MediaEngineStore}catch{return null}}
  function getVolume(id,c=DEFAULT){try{const v=store()?.getLocalVolume(id,c);return Number.isFinite(v)?v:100}catch{return 100}}
  function remember(c,id,v){
    if(!validId(id)||!validContext(c)||!Number.isFinite(v)||v<0)return;
    const k=keyFor(c,id);
    if(v>amp(STOCK)+0.001){
      const n=Math.min(v,amp(LIMIT));
      if(saved[k]===n)return;
      saved={...saved,[k]:n};
    }else{
      if(!(k in saved))return;
      const copy={...saved};delete copy[k];saved=copy;
    }
    storage?.set({overrides:saved}).catch(()=>{});
  }
  function setVolume(id,p,c=DEFAULT){
    if(!active||!validId(id)||!actions)return false;
    const a=toAmp(p);
    actions.setLocalVolume(id,a,c);
    if(p>STOCK)log("local volume requested "+Math.round(p)+"% amplitude="+a.toFixed(2)+" context="+c);
    return true;
  }
  function reapply(){
    timer=null;
    if(!active||!dispatcher)return;
    applying=true;
    try{
      for(const [key,value] of Object.entries(saved)){
        const [context,userId]=key.split(":");
        if(!validId(userId)||!validContext(context)||!Number.isFinite(value)||value<=amp(STOCK))continue;
        const volume=Math.min(amp(LIMIT),value);
        if(Math.abs(getVolume(userId,context)-volume)<0.001)continue;
        dispatcher.dispatch({type:"AUDIO_SET_LOCAL_VOLUME",context,userId,volume});
      }
    }catch(e){log("restore failed: "+String(e))}
    finally{applying=false}
  }
  function schedule(){if(!active)return;clearTimeout(timer);timer=setTimeout(reapply,100)}
  function isElement(React,n){return React.isValidElement(n)}
  function raiseTree(React,node,depth=0){
    if(depth>8)return node;
    if(Array.isArray(node))return node.map(n=>raiseTree(React,n,depth+1));
    if(!isElement(React,node))return node;
    const p=node.props||{};
    if(typeof p.value==="number"&&typeof p.onChange==="function"&&p.maxValue===STOCK)
      return React.cloneElement(node,{maxValue:LIMIT});
    if(typeof p.value==="number"&&typeof p.onValueChange==="function"&&(p.maxVolume==null||p.maxVolume===STOCK))
      return React.cloneElement(node,{maxVolume:LIMIT});
    if(p.children!=null){
      const next=raiseTree(React,p.children,depth+1);
      if(next!==p.children)return React.cloneElement(node,{children:next});
    }
    return node;
  }
  function editProfile(React,node,id,depth=0){
    if(depth>12)return node;
    if(Array.isArray(node)){
      let changed=false;const result=[];
      for(const child of node){
        if(isElement(React,child)&&child.key==="set-volume"){
          result.push(raiseTree(React,child),React.createElement(ExactControls,{key:"vexcord-volume-exact",id}));
          changed=true;
        }else{const next=editProfile(React,child,id,depth+1);changed ||=next!==child;result.push(next)}
      }
      return changed?result:node;
    }
    if(!isElement(React,node))return node;
    const p=node.props||{};
    if(p.children==null)return node;
    const next=editProfile(React,p.children,id,depth+1);
    return next===p.children?node:React.cloneElement(node,{children:next});
  }
  function ExactControls({id}){
    const {React,ReactNative:RN}=valenced.react,h=React.createElement;
    const subscribe=React.useCallback(fn=>{const s=store();if(!s)return()=>{};s.addChangeListener(fn);return()=>s.removeChangeListener(fn)},[]);
    const read=React.useCallback(()=>getVolume(id),[id]);
    const a=React.useSyncExternalStore(subscribe,read,read);
    const p=Math.round(toPercent(a));
    const [entry,setEntry]=React.useState(String(p));
    const [editing,setEditing]=React.useState(false);
    React.useEffect(()=>{if(!editing)setEntry(String(p))},[p,editing]);
    const commit=()=>{setEditing(false);if(/^\d{1,3}(?:[.,]\d{1,2})?$/.test(entry.trim()))setVolume(id,bounded(Number(entry.replace(",","."))));else setEntry(String(p))};
    const btn=(label,next)=>h(RN.Pressable,{accessibilityRole:"button",accessibilityLabel:label,onPress:()=>setVolume(id,next),style:{paddingHorizontal:15,paddingVertical:10,borderRadius:10,backgroundColor:"#343341"}},h(RN.Text,{style:{color:"#eee9ff",fontSize:16,fontWeight:"600"}},label));
    return h(RN.View,{style:{marginHorizontal:12,marginBottom:8,padding:10,borderRadius:12,backgroundColor:"#252530"}},
      h(RN.View,{style:{flexDirection:"row",alignItems:"center",gap:8}},
        btn("−",p-5),
        h(RN.TextInput,{value:entry,onFocus:()=>setEditing(true),onChangeText:setEntry,onEndEditing:commit,onSubmitEditing:commit,keyboardType:"decimal-pad",returnKeyType:"done",accessibilityLabel:"User volume percent",style:{flex:1,textAlign:"center",fontSize:17,color:"#eee9ff",padding:8}}),
        h(RN.Text,{style:{color:"#c9b3ff"}},"%"),btn("+",p+5)),
      h(RN.View,{style:{flexDirection:"row",justifyContent:"space-between",marginTop:8}},
        ...[100,200,300,400,500].map(n=>h(RN.Pressable,{key:n,accessibilityRole:"button",onPress:()=>setVolume(id,n),style:{padding:7}},h(RN.Text,{style:{color:n===p?"#d8c4ff":"#bcb7cd",fontSize:12,fontWeight:n===p?"700":"400"}},n+"%"))))
    );
  }
  const wrappers=new WeakMap(),outerWrappers=new WeakMap();
  function wrapInner(React,inner){
    if(wrappers.has(inner))return wrappers.get(inner);
    function VxVolumeVoiceSettings(props){
      const rendered=inner(props);
      if(!active||!validId(props?.user?.id))return rendered;
      try{const patched=editProfile(React,rendered,props.user.id);if(patched!==rendered&&!profileReady){profileReady=true;log("profile volume row hooked");Promise.resolve().then(notify)}return patched}catch(e){log("profile render: "+String(e));return rendered}
    }
    wrappers.set(inner,VxVolumeVoiceSettings);
    wrappers.set(VxVolumeVoiceSettings,VxVolumeVoiceSettings);
    return VxVolumeVoiceSettings;
  }
  function transformOuter(React,props,result){
    if(!active||!validId(props?.user?.id)||props.user.id===props.currentUser?.id||!isElement(React,result)||typeof result.type!=="function")return result;
    return React.createElement(wrapInner(React,result.type),{...result.props,key:result.key});
  }
  function isStreamLabel(label){
    if(typeof label!=="string")return false;
    try{if(i18n?.t?.pEAl4b!=null&&i18n.intl.string(i18n.t.pEAl4b)===label)return true}catch{}
    return /^(stream volume|streamlautstärke|stream-lautstärke)$/i.test(label);
  }
  function SettingsComponent(){
    const {React,ReactNative:RN}=valenced.react,h=React.createElement;
    const [,refresh]=React.useReducer(n=>n+1,0);
    React.useEffect(()=>{listeners.add(refresh);return()=>listeners.delete(refresh)},[]);
    return h(RN.ScrollView,{contentContainerStyle:{padding:18}},
      h(RN.Text,{style:{fontSize:24,fontWeight:"700",color:"#c9b3ff"}},"Volume Maxer 1.1"),
      h(RN.Text,{style:{fontSize:16,color:"#eee9ff",marginVertical:14}},"0–500% · Nutzer und Streams"),
      ...[[!!actions,"Audio Actions"],[profileReady,"Profilregler live erkannt"],[pendingReady,"Synchronisationsschutz"],[streamReady,"Stream-Regler erkannt"]].map(([ok,label])=>h(RN.Text,{key:label,style:{color:ok?"#85e6ca":"#bcb7cd",marginTop:8}},(ok?"✓ ":"… ")+label)),
      h(RN.Text,{style:{color:"#bcb7cd",marginTop:18}},"Im Profil eines Gesprächsteilnehmers stehen zusätzlich zum Regler exakte Prozentwerte, +/- und Presets bereit. Hohe Werte werden nur lokal gespeichert. Senke die Lautstärke vor dem Ausprobieren."),
      h(RN.Pressable,{onPress:()=>{for(const k of Object.keys(saved)){const [c,id]=k.split(":");setVolume(id,STOCK,c)}saved={};storage?.set({overrides:{}}).catch(()=>{});notify()},style:{marginTop:18,padding:12,borderRadius:12,backgroundColor:"#343341"}},h(RN.Text,{style:{color:"#eee9ff",textAlign:"center"}},"Alle Boost-Werte auf 200% zurücksetzen")));
  }
  return {default:vexcord.definePlugin({
    async start(api){
      active=true;profileReady=false;pendingReady=false;streamReady=false;actions=null;i18n=null;conversion=null;
      const {React}=valenced.react;
      dispatcher=valenced.discord.common.flux.Dispatcher;
      storage=api.vexcord.storage.open("volumes",{overrides:{}});
      api.cleanup(()=>{active=false;clearTimeout(timer);timer=null;actions=null;dispatcher=null;notify()});
      try{saved=(await storage.get()).overrides||{}}catch{saved={}}
      if(!active)return;
      api.vexcord.modules.observe(["setLocalVolume","toggleLocalMute"],m=>{if(!active)return;actions=m;log("audio action bound");notify()});
      api.vexcord.modules.observe(["amplitudeToPerceptual","perceptualToAmplitude"],m=>{if(active)conversion=m});
      api.vexcord.modules.observe(["intl","t"],m=>{if(active&&typeof m.intl?.string==="function")i18n=m});
      api.vexcord.modules.observe(["updatePendingSettings","drainPendingAudioSettings","getPendingAudioSettings"],m=>{
        if(!active)return;
        api.vexcord.hooks.before(m,"updatePendingSettings",args=>{
          const [c,id,settings]=args,v=settings?.volume;
          if(!validContext(c)||!validId(id)||!Number.isFinite(v))return args;
          if(!applying)remember(c,id,v);
          if(v>amp(STOCK))return [c,id,{...settings,volume:amp(STOCK)},...args.slice(3)];
          return args;
        });
        pendingReady=true;log("account sync cap hook bound");notify();
      });
      api.vexcord.events.observe("AUDIO_SET_LOCAL_VOLUME",e=>{
        if(!applying)remember(e.context||DEFAULT,e.userId,e.volume);
        if(Number.isFinite(e.volume)&&e.volume>amp(STOCK)){
          const id=e.userId,c=e.context||DEFAULT;
          setTimeout(()=>{if(active)log("local store readback "+Math.round(toPercent(getVolume(id,c)))+"%")},50);
        }
      });
      for(const event of ["USER_SETTINGS_PROTO_UPDATE","POST_CONNECTION_OPEN"])api.vexcord.events.observe(event,schedule);
      const pathFinder=valenced.discord.utils.modules.finders;
      api.cleanup(pathFinder.getModuleWithImportedPath("modules/user_profile/native/UserProfileVoiceSettings.tsx",m=>{
        if(!active||typeof m.default!=="function")return;
        const original=m.default;
        api.cleanup(valenced.patcher.instead(m,"default",([props],orig)=>transformOuter(React,props,orig(props))));
        api.cleanup(valenced.react.jsxRuntime.insteadJSX(original,([type,props,key],jsx)=>{
          const PatchedOuter=outerWrappers.get(type)||function VxVolumeProfile(p){return transformOuter(React,p,type(p))};
          outerWrappers.set(type,PatchedOuter);
          return jsx(PatchedOuter,props,key);
        }));
        log("exact profile module bound");
      }));
      const found=new WeakSet();
      const patchSlider=(type,property)=>{
        if(!active||!type||found.has(type))return;
        found.add(type);
        api.cleanup(valenced.react.jsxRuntime.beforeJSX(type,([t,p,k])=>{
          if(!active||!isStreamLabel(p?.label||p?.accessibilityLabel))return [t,p,k];
          if(property==="maxVolume"?p.maxVolume!=null&&p.maxVolume!==STOCK:p[property]!==STOCK)return [t,p,k];
          if(!streamReady){streamReady=true;log("stream slider hooked");Promise.resolve().then(notify)}
          return [t,{...p,[property]:LIMIT},k];
        }));
      };
      const finders=valenced.modules.finders;
      api.cleanup(finders.getModules(finders.filters.withName("VolumeSlider"),m=>patchSlider(m,"maxVolume"),{cached:false,max:4}));
      api.vexcord.modules.observe(["TableSliderRow","TableRow"],m=>patchSlider(m.TableSliderRow,"maxValue"));
      api.vexcord.modules.observe(["Slider","TableRow","Button"],m=>patchSlider(m.Slider,"maxValue"));
      api.vexcord.modules.observe(["maximum","useValue","onValueChange","useTitle"],m=>{
        if(m.maximum!==STOCK||!(m.onValueChange?.name==="onStreamValueSettingValueChange"||m.useValue?.name==="useStreamVolumeSettingValue"))return;
        try{m.maximum=LIMIT;if(m.maximum===LIMIT){streamReady=true;api.cleanup(()=>{if(m.maximum===LIMIT)m.maximum=STOCK});notify()}}catch{}
      });
      schedule();
      log("1.1.0 ready; waiting for profile render");
    },SettingsComponent
  })};
})()
