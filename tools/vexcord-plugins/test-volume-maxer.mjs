import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const id='123456789012345678', second='234567890123456789';
const children=[], cleanup=[], observers=[], events={}, hooks=[], jsxHooks=[], logs=[], writes=[], sent=[];
let saved={}, actual=100, pending, profileHook;
const sliderType=()=>null;
const el=(type,props={})=>({type,key:props.key??null,props});
const React={
 createElement:(type,props,...kids)=>el(type,{...props,...(kids.length?{children:kids.length===1?kids[0]:kids}:{})}),
 isValidElement:n=>!!n&&typeof n==='object'&&'type'in n&&'props'in n,
 cloneElement:(n,p)=>({...n,props:{...n.props,...p}}),
 useCallback:fn=>fn,useSyncExternalStore:(_,read)=>read(),useState:x=>[x,()=>{}],useEffect:()=>{},useReducer:()=>[0,()=>{}]
};
const store={getLocalVolume:()=>actual,addChangeListener(){},removeChangeListener(){}};
const dispatcher={dispatch(e){sent.push(e);actual=e.volume;events[e.type]?.(e)}};
const actions={setLocalVolume(userId,volume,context='default'){dispatcher.dispatch({type:'AUDIO_SET_LOCAL_VOLUME',userId,volume,context});const a=pending([context,userId,{volume}]);writes.push(a[2]);},toggleLocalMute(){}};
function Inner(p){return el('root',{children:[el('group',{key:'set-volume',children:[el(sliderType,{value:actual,maxValue:200,onChange:n=>actions.setLocalVolume(p.user.id,n)}),el(sliderType,{value:100,maxValue:200,onChange(){}})]}),el('unrelated',{maxValue:200})]})}
const module={default:p=>el(Inner,p)};
const valenced={
 react:{React,ReactNative:{View:'View',Text:'Text',Pressable:'Pressable',TextInput:'TextInput',ScrollView:'ScrollView'},jsxRuntime:{insteadJSX(t,h){jsxHooks.push({t,h});return()=>{}},beforeJSX(t,h){jsxHooks.push({t,h});return()=>{}}}},
 patcher:{instead(m,k,h){profileHook=h;return()=>{}}},
 discord:{flux:{Stores:{MediaEngineStore:store}},common:{flux:{Dispatcher:dispatcher}},utils:{modules:{finders:{getModuleWithImportedPath(path,fn){assert.equal(path,'modules/user_profile/native/UserProfileVoiceSettings.tsx');fn(module);return()=>{}}}}}},
 modules:{finders:{filters:{withName:n=>n},getModules(n,f){return()=>{}}}}
};
const api={cleanup:f=>cleanup.push(f),vexcord:{
 storage:{open:()=>({get:async()=>({overrides:{}}),set:async x=>{saved=x.overrides}})},
 modules:{observe:(keys,fn)=>observers.push({keys,fn})},hooks:{before:(m,k,f)=>{pending=f}},events:{observe:(e,f)=>events[e]=f}
}};
const timers=[];
const {default:plugin}=vm.runInNewContext(fs.readFileSync('plugins/volume-maxer/index.js','utf8'),{
 valenced,vexcord:{definePlugin:x=>x},nativeLoggingHook:s=>logs.push(s),
 setTimeout:f=>{timers.push(f);return timers.length},clearTimeout:()=>{},Promise,Math,Number,Set,WeakMap,WeakSet,Object
});
await plugin.start(api)
const emit=(k,m)=>observers.find(x=>x.keys.includes(k)).fn(m);
emit('setLocalVolume',actions)
emit('updatePendingSettings',{updatePendingSettings(){},drainPendingAudioSettings(){},getPendingAudioSettings(){}})
const outer=profileHook([{user:{id},currentUser:{id:second}}],module.default);
const patched=outer.type(outer.props);
const rows=patched.props.children;
assert.equal(rows[0].props.children[0].props.maxValue,500);
assert.equal(rows[0].props.children[1].props.maxValue,500);
assert.equal(rows[2].props.maxValue,200)
assert.equal(rows[1].type.name,'ExactControls')
const controls=rows[1].type(rows[1].props);
const presets=controls.props.children[1].props.children;
presets.at(-1).props.onPress();
assert(sent.at(-1).volume>1500 && sent.at(-1).volume<1600);
assert(Math.abs(writes.at(-1).volume-199.5262315)<0.01)
assert.equal(saved['default:'+id],sent.at(-1).volume)
const boost=actual;actual=100;events.USER_SETTINGS_PROTO_UPDATE();timers.at(-1)();assert.equal(actual,boost)
presets[0].props.onPress();assert.equal(actual,100);assert.equal(Object.keys(saved).length,0)
const own=profileHook([{user:{id},currentUser:{id}}],module.default)
assert.equal(own.type,Inner)
for(const f of cleanup.reverse())f()
console.log('PASS exact profile patch, both slider limits, 500% audio action, local persistence, sync restoration, stock output, own profile and reset');
