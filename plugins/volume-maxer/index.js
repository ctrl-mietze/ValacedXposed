(() => {
  // Discord Android HBC 98: VolumeSlider accepts maxVolume in perceptual units.
  // The existing slider translates perceptualToAmplitude before calling setLocalVolume.
  const LIMIT=500, STOCK=200;
  let active=false, voiceReady=false, streamReady=false, conversions=null, i18n=null;
  const listeners=new Set();
  const streamNames=new Set(["Stream Volume","Stream volume","Streamlautstärke","Stream-Lautstärke"]);
  const notify=()=>{for(const fn of listeners)fn()};
  const isStreamDefinition=p=>p && p.maximum===STOCK &&
    typeof p.onValueChange==="function" && typeof p.useValue==="function" &&
    (p.onValueChange.name==="onStreamValueSettingValueChange" || p.useValue.name==="useStreamVolumeSettingValue");
  function isStreamLabel(label){
    if(typeof label!=="string")return false;
    try{
      const key=i18n?.t?.pEAl4b;
      if(key!=null && label===i18n.intl.string(key))return true;
    }catch{}
    return streamNames.has(label);
  }
  function target(props,key){
    if(!props || typeof props.value!=="number" || !Number.isFinite(props.value) ||
      typeof props.onValueChange!=="function" || (props.maxVolume!=null && props.maxVolume!==STOCK))return false;
    return key==="set-volume" || isStreamLabel(props.accessibilityLabel);
  }
  function boostProps(props){
    return {...props,maxVolume:LIMIT};
  }
  function BoostedSlider({component,original}){
    const {React,ReactNative:RN}=valenced.react;
    const h=React.createElement;
    const [value,setValue]=React.useState(original.value);
    React.useEffect(()=>setValue(original.value),[original.value]);
    let percent=null;
    try{
      const p=conversions?.amplitudeToPerceptual(value);
      if(Number.isFinite(p))percent=Math.round(p);
    }catch{}
    const changed=v=>{setValue(v);return original.onValueChange(v)};
    return h(RN.View,{style:[{flexGrow:1,minWidth:0},original.style]},
      h(component,{...boostProps(original),style:undefined,onValueChange:changed}),
      h(RN.Text,{style:{color:"#a99cce",fontSize:11,textAlign:"right",marginRight:8,marginTop:2}},
        percent===null?"Max. 500%":percent+"% / 500%")
    );
  }
  function SettingsComponent(){
    const {React,ReactNative:RN}=valenced.react;
    const [,refresh]=React.useReducer(n=>n+1,0);
    React.useEffect(()=>{listeners.add(refresh);return()=>listeners.delete(refresh)},[]);
    const h=React.createElement,T=RN.Text,V=RN.View;
    return h(RN.ScrollView,{contentContainerStyle:{padding:18}},
      h(T,{style:{fontSize:24,fontWeight:"700",color:"#c9b3ff"}},"Volume Maxer"),
      h(T,{style:{fontSize:14,color:"#bcb7cd",marginTop:8}},"Individuelle Lautstärke für Nutzer und Streams"),
      h(V,{style:{padding:18,borderRadius:16,backgroundColor:"#202234",marginTop:18}},
        h(T,{style:{fontSize:36,fontWeight:"800",color:"#f1eaff"}},"500%"),
        h(T,{style:{color:"#a99cce",marginTop:4}},"Maximale Reglerlautstärke"),
        h(T,{style:{color:voiceReady?"#85e6ca":"#bcb7cd",marginTop:12}},voiceReady?"Voice-Slider erkannt":"Voice-Slider wird gesucht"),
        h(T,{style:{color:streamReady?"#85e6ca":"#bcb7cd",marginTop:5}},streamReady?"Stream-Regler erkannt":"Stream-Regler wird bei Bedarf gesucht")),
      h(T,{style:{color:"#bcb7cd",marginTop:16,fontSize:13}},"Öffne im Voice-Chat das Profil eines Teilnehmers und verändere die Nutzerlautstärke. Der Originalregler reicht bis 500%. Discords eigene Verstärkungskurve bleibt erhalten. Der native Audiopfad kann zusätzlich begrenzen."),
      h(T,{style:{color:"#bcb7cd",marginTop:12,fontSize:13}},"Vor dem Deaktivieren hohe Nutzer- oder Streamwerte wieder auf 200% oder weniger einstellen. Hohe Lautstärke kann verzerren und das Gehör belasten."));
  }
  return {default:vexcord.definePlugin({
    start(api){
      active=true;voiceReady=false;streamReady=false;conversions=null;i18n=null;
      const finders=valenced.modules.finders;
      const registered=new WeakSet();
      const definitions=new WeakSet();
      const definitionDisposers=[];
      api.cleanup(()=>{
        active=false;voiceReady=false;streamReady=false;conversions=null;i18n=null;
        for(const restore of definitionDisposers.reverse())restore();
        notify();
      });
      api.vexcord.modules.observe(["amplitudeToPerceptual","perceptualToAmplitude"],m=>{if(active)conversions=m});
      api.vexcord.modules.observe(["intl","t"],m=>{if(active && typeof m.intl?.string==="function")i18n=m});
      api.cleanup(finders.getModules(finders.filters.withName("VolumeSlider"),component=>{
        if(!active || typeof component!=="function" || registered.has(component))return;
        registered.add(component);voiceReady=true;notify();
        api.cleanup(valenced.react.jsxRuntime.insteadJSX(component,([type,props,key],jsx)=>{
          if(!active || !target(props,key))return jsx(type,props,key);
          if(!streamReady && isStreamLabel(props.accessibilityLabel)){streamReady=true;Promise.resolve().then(notify)}
          // Preserve the original component, audio callbacks and native slider.
          return jsx(BoostedSlider,{component:type,original:props},key);
        }));
      },{cached:false,max:4}));
      function extendDefinition(def){
        if(!active || !isStreamDefinition(def) || definitions.has(def))return;
        try{
          def.maximum=LIMIT;
          if(def.maximum!==LIMIT)return;
          definitions.add(def);streamReady=true;notify();
          definitionDisposers.push(()=>{try{if(def.maximum===LIMIT)def.maximum=STOCK}catch{}});
        }catch{}
      }
      // Handle the newer Settings V2 definition, including already-initialized modules.
      api.vexcord.modules.observe(["maximum","useValue","onValueChange","useTitle"],extendDefinition);
      api.vexcord.modules.observe(["createVolumeSlider","createSlider","createToggle"],m=>{
        if(!active || typeof m.createVolumeSlider!=="function")return;
        api.vexcord.hooks.after(m,"createVolumeSlider",def=>{extendDefinition(def);return def});
      });
    },
    SettingsComponent
  })};
})()
