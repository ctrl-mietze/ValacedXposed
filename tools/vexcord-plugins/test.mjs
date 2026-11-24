import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import path from 'node:path'
const root=path.resolve('plugins')
function load(name,scenario){
  const timers=new Set(),cleanups=[],activity=[],requests=[],hooks=[],diagnostics=[];let published=null
  const route={backend:5,a2dp:true,leAudio:true,wired:false,enabled:false,modeHook:true,hookHits:0,note:'idle'}
  const nativeConfigs=[]
  const audio={on:0,off:0,setCommunicationModeOn(){this.on++;return 'normal'},setCommunicationModeOff(){this.off++}}
  const native={
    getNativeModule(){return audio},
    callNativeMethodSync(method,args){
      if(method==='vexcord.bitchord.status'){diagnostics.push(JSON.parse(args[0]));return null}
      if(method==='vexcord.audio.configure'){nativeConfigs.push(Array.from(args));route.enabled=args[0];route.preference=args[1];return {...route}}
      assert.equal(method,'vexcord.audio.route');return {...route}
    },
    async callNativeMethod(method){
      requests.push(method)
      if(method==='vexcord.bitchord.session')return JSON.stringify(scenario.media)
      if(method==='vexcord.bitchord.stop')return null
      throw Error(method)
    },
  }
  const rest={async post(req){assert.equal(req.url,'/applications/1521756469433991299/external-assets');assert.deepEqual(Array.from(req.body.urls),['https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg']);return {ok:true,body:[{external_asset_path:'external/fixture'}]}}}
  const valenced={modules:{native,finders:{filters:{withProps:(...args)=>args},lookupModule:()=>[rest]}},react:{},
    discord:{common:{flux:{Dispatcher:{dispatch(payload){activity.push(payload);published=payload.activity}}}},flux:{Stores:{UserStore:{getCurrentUser:()=>scenario.loggedOut?null:{id:'fixture-account'}},PresenceStore:{getActivities:()=>published?[published]:[]}},onFluxEventDispatched:()=>()=>{}}},
    patcher:{
      before(target,key,fn){const original=target[key];target[key]=function(...args){return original.apply(this,fn(args))};hooks.push(key);return()=>{target[key]=original}},
      instead(target,key,fn){const original=target[key];target[key]=function(...args){return fn.call(this,args,original)};hooks.push(key);return()=>{target[key]=original}}
    }
  }
  const globals={valenced,vexcord:{definePlugin:x=>x},URL,Date,JSON,Reflect,Number,Math,Map,Set,Array,Promise,
    nativeLoggingHook(){},setTimeout,clearTimeout,setInterval(fn){timers.add(fn);return fn},clearInterval(fn){timers.delete(fn)}}
  const plugin=vm.runInNewContext(fs.readFileSync(path.join(root,name,'index.js'),'utf8'),globals).default
  const api={plugin:{},cleanup:fn=>cleanups.push(fn),vexcord:{storage:{open:(_name,defaults)=>({
    async get(){return {...defaults,...scenario.config}},async set(){}})}}}
  return {plugin,api,route,audio,timers,cleanups,activity,requests,hooks,nativeConfigs,diagnostics,
    async tick(){for(const fn of timers)fn();await new Promise(r=>setImmediate(r));await new Promise(r=>setImmediate(r))},
    stop(){for(const fn of cleanups)fn()}
  }
}
const audio=load('stereo-call',{})
await audio.plugin.start(audio.api)
assert.deepEqual(audio.nativeConfigs,[[true,'auto']])
assert.equal(audio.hooks.length,0)
assert.equal(audio.route.enabled,true)
await audio.tick()
audio.stop()
assert.deepEqual(audio.nativeConfigs,[[true,'auto'],[false,'auto']])
assert.equal(audio.timers.size,0)
const classic=load('stereo-call',{config:{routePreference:'a2dp'}})
await classic.plugin.start(classic.api)
assert.deepEqual(classic.nativeConfigs,[[true,'a2dp']])
classic.stop()
const disabledAudio=load('stereo-call',{config:{preferStereo:false}})
await disabledAudio.plugin.start(disabledAudio.api)
assert.deepEqual(disabledAudio.nativeConfigs,[[false,'auto']])
disabledAudio.stop()
const now=Date.now()
const fixture={media:{status:'connected',playing:true,title:'Fixture Track',artist:'Fixture Artist',album:'Fixture Album',
 art:'https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg',mediaUri:'https://music.youtube.com/watch?v=abcdefghijk',
 position:30000,duration:180000,sampleTime:now}}
const presence=load('bitchord-presence',fixture)
await presence.plugin.start(presence.api);await presence.tick()
assert.equal(presence.activity.length,2)
const a=presence.activity.at(-1)
assert.equal(presence.activity[0].activity.assets,undefined)
assert.equal(a.type,'LOCAL_ACTIVITY_UPDATE');assert.equal(a.socketId,'Vexcord:BitChord')
assert.equal(a.activity.application_id,'1521756469433991299')
assert.equal(a.activity.type,2);assert.equal(a.activity.details,'Fixture Track')
assert.equal(a.activity.state,'Fixture Artist · Fixture Album')
assert.equal(a.activity.assets.large_image,'mp:external/fixture')
assert.equal(a.activity.timestamps.start,now-30000)
assert.equal(a.activity.timestamps.end,now+150000)
assert.equal(a.activity.metadata.button_urls[0],'https://music.youtube.com/watch?v=abcdefghijk')
assert.equal(presence.diagnostics.at(-1).publication,'observed')
await presence.tick();assert.equal(presence.activity.length,2)
fixture.media.playing=false;fixture.media.playbackState=2;await presence.tick();assert.equal(presence.activity.at(-1).activity,null)
await presence.tick();assert.equal(presence.activity.length,3)
fixture.media.playing=true;fixture.media.playbackState=3;fixture.media.art='https://i.ytimg.com/image.jpg?token=private';fixture.media.title='New fixture'
await presence.tick();assert(!presence.activity.at(-1).activity.assets)
presence.stop();assert.equal(presence.activity.at(-1).activity,null);assert.equal(presence.timers.size,0)
assert.equal(presence.requests.at(-1),'vexcord.bitchord.stop')
const disabled=load('bitchord-presence',{config:{publish:false},media:fixture.media});await disabled.plugin.start(disabled.api);await disabled.tick();assert.equal(disabled.activity.length,0);assert.equal(disabled.requests.length,0);disabled.stop()
const signedOut=load('bitchord-presence',{loggedOut:true,media:fixture.media});await signedOut.plugin.start(signedOut.api);await signedOut.tick();assert.equal(signedOut.activity.length,0);assert.equal(signedOut.requests.length,0);assert.equal(signedOut.diagnostics.at(-1).discord,'waiting');signedOut.stop()
const paused=load('bitchord-presence',{config:{showPaused:true},media:{...fixture.media,playing:false,playbackState:2}});await paused.plugin.start(paused.api);await paused.tick();assert.equal(paused.activity.at(-1).activity.type,0);assert(paused.activity.at(-1).activity.state.startsWith('Pausiert'));assert.equal(paused.activity.at(-1).activity.timestamps,undefined);paused.stop()
for(const status of presence.diagnostics)assert.deepEqual(Object.keys(status).sort(),['cover','discord','media','publication']);
console.log('PASS native controller lifecycle, audio preferences, BitChord activity, album art, timestamps, pause, privacy and lifecycle')
