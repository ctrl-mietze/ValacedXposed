import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const cleanups=[], observers=[], patches=[], effects=[];
let componentHook, observedName, lastState;
function VolumeSlider(){}
const React={
  createElement(type,props,...children){return {type,props,children}},
  useState(value){return [value,v=>{lastState=v}]},
  useEffect(fn){effects.push(fn)}
};
const RN={View:'View',Text:'Text'};
const valenced={
  react:{React,ReactNative:RN,jsxRuntime:{insteadJSX(type,hook){assert.equal(type,VolumeSlider);componentHook=hook;return()=>{componentHook=null}}}},
  modules:{finders:{filters:{withName:name=>name},getModules(name,fn){observedName=name;fn(VolumeSlider);return()=>{}}}}
};
const api={cleanup:fn=>cleanups.push(fn),vexcord:{
  modules:{observe(keys,fn){observers.push({keys,fn})}},
  hooks:{after(target,key,hook){patches.push({target,key,hook})}}
}};
const {default:plugin}=vm.runInNewContext(fs.readFileSync('plugins/volume-maxer/index.js','utf8'),{vexcord:{definePlugin:x=>x},valenced,Set,WeakSet,Math,Number,Promise});
plugin.start(api);
assert.equal(observedName,'VolumeSlider');
const emit=(key,obj)=>observers.find(x=>x.keys.includes(key)).fn(obj);
emit('amplitudeToPerceptual',{amplitudeToPerceptual:x=>x,perceptualToAmplitude:x=>x});
emit('intl',{t:{pEAl4b:'stream'},intl:{string:k=>k==='stream'?'Transmisión':null}});
const calls=[];
const original={value:100,onValueChange:x=>calls.push(x),style:{margin:8}};
const jsx=(type,props,key)=>({type,props,key});
const voice=componentHook([VolumeSlider,original,'set-volume'],jsx);
assert.notEqual(voice.type,VolumeSlider);
const wrapped=voice.type(voice.props);
const slider=wrapped.children[0];
assert.equal(slider.type,VolumeSlider);
assert.equal(slider.props.maxVolume,500);
assert.equal(slider.props.value,100);
slider.props.onValueChange(500);
assert.equal(calls.at(-1),500);
assert.equal(lastState,500);
assert.equal(wrapped.children[1].children[0],'100% / 500%');
assert.notEqual(componentHook([VolumeSlider,{...original,accessibilityLabel:'Transmisión'},null],jsx).type,VolumeSlider);
assert.notEqual(componentHook([VolumeSlider,{...original,accessibilityLabel:'Streamlautstärke'},null],jsx).type,VolumeSlider);
for(const props of [
  {...original,accessibilityLabel:'Ausgabelautstärke'},
  {...original,accessibilityLabel:'Output Volume'},
  {...original,maxVolume:100,accessibilityLabel:'Streamlautstärke'},
  {...original,value:NaN},
  {...original,onValueChange:undefined}
]) assert.equal(componentHook([VolumeSlider,props,null],jsx).props,props);
const stream={maximum:200,useValue:function useStreamVolumeSettingValue(){},onValueChange:function onStreamValueSettingValueChange(){}};
const output={maximum:200,useValue:function useOutputVolumeSettingValue(){},onValueChange(){}};
emit('maximum',stream);emit('maximum',output);
assert.equal(stream.maximum,500);
assert.equal(output.maximum,200);
emit('createVolumeSlider',{createVolumeSlider(){},createSlider(){},createToggle(){}});
const future={...stream,maximum:200};
assert.equal(patches[0].hook(future),future);
assert.equal(future.maximum,500);
const unrelated={...output,maximum:100};
assert.equal(patches[0].hook(unrelated),unrelated);
assert.equal(unrelated.maximum,100);
for(const cleanup of cleanups.reverse())cleanup();
assert.equal(componentHook,null);
assert.equal(stream.maximum,200);
assert.equal(future.maximum,200);
console.log('PASS Volume Maxer: native slider 500%, real callback, translated stream, unrelated sliders unchanged, V2 definitions, cleanup');
