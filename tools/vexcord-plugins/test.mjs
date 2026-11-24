import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import path from 'node:path'
const root=path.resolve('plugins')
function load(name,scenario){
  const timers=new Set(),cleanups=[],activity=[],requests=[],hooks=[]
  const route={eligible:true,a2dp:true,leAudio:false,wired:false}
  const audio={on:0,off:0,setCommunicationModeOn(){this.on++;return 'normal'},setCommunicationModeOff(){this.off++}}
  const native={
    getNativeModule(){return audio},
    callNativeMethodSync(method){assert.equal(method,'vexcord.audio.route');return {...route}},
    async callNativeMethod(method){
      requests.push(method)
      if(method==='vexcord.bitchord.session')return JSON.stringify(scenario.media)
      if(method==='vexcord.bitchord.stop')return null
      throw Error(method)
    },
  }
  const rest={async post(req){assert.equal(req.url,'/applications/1521756469433991299/external-assets');assert.deepEqual(Array.from(req.body.urls),['https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg']);return {ok:true,body:[{external_asset_path:'external/fixture'}]}}}
  const valenced={modules:{native,finders:{filters:{withProps:(...args)=>args},lookupModule:()=>[rest]}},react:{},
    discord:{common:{flux:{Dispatcher:{dispatch(payload){activity.push(payload)}}}}},
    patcher:{
      before(target,key,fn){const original=target[key];target[key]=function(...args){return original.apply(this,fn(args))};hooks.push(key);return()=>{target[key]=original}},
      instead(target,key,fn){const original=target[key];target[key]=function(...args){return fn.call(this,args,original)};hooks.push(key);return()=>{target[key]=original}}
    }
  }
  const globals={valenced,vexcord:{definePlugin:x=>x},URL,Date,JSON,Reflect,Number,Math,Map,Set,Array,Promise,
    nativeLoggingHook(){},setInterval(fn){timers.add(fn);return fn},clearInterval(fn){timers.delete(fn)}}
  const plugin=vm.runInNewContext(fs.readFileSync(path.join(root,name,'index.js'),'utf8'),globals).default
  const api={plugin:{},cleanup:fn=>cleanups.push(fn),vexcord:{storage:{open:(_name,defaults)=>({
    async get(){return {...defaults,...scenario.config}},async set(){}})}}}
  return {plugin,api,route,audio,timers,cleanups,activity,requests,hooks,
    async tick(){for(const fn of timers)fn();await new Promise(r=>setImmediate(r));await new Promise(r=>setImmediate(r))},
    stop(){for(const fn of cleanups)fn()}
  }
}
const audio=load('stereo-call',{})
await audio.plugin.start(audio.api)
assert.deepEqual(audio.hooks,['setCommunicationModeOn','setCommunicationModeOff'])
assert.equal(audio.audio.setCommunicationModeOn(),undefined)
assert.equal(audio.audio.on,0)
audio.route.eligible=false;await audio.tick();assert.equal(audio.audio.on,1)
audio.audio.setCommunicationModeOn();assert.equal(audio.audio.on,2)
audio.route.eligible=true;audio.audio.setCommunicationModeOn();audio.audio.setCommunicationModeOff()
assert.equal(audio.audio.off,1)
audio.stop();assert.equal(audio.audio.on,2);assert.equal(audio.timers.size,0)
assert.equal(audio.audio.setCommunicationModeOn(),'normal')
const now=Date.now()
const fixture={media:{status:'connected',playing:true,title:'Fixture Track',artist:'Fixture Artist',album:'Fixture Album',
 art:'https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg',mediaUri:'https://music.youtube.com/watch?v=abcdefghijk',
 position:30000,duration:180000,sampleTime:now}}
const presence=load('bitchord-presence',fixture)
await presence.plugin.start(presence.api);await presence.tick()
assert.equal(presence.activity.length,1)
const a=presence.activity[0]
assert.equal(a.type,'LOCAL_ACTIVITY_UPDATE');assert.equal(a.socketId,'Vexcord:BitChord')
assert.equal(a.activity.application_id,'1521756469433991299')
assert.equal(a.activity.type,2);assert.equal(a.activity.details,'Fixture Track')
assert.equal(a.activity.state,'Fixture Artist · Fixture Album')
assert.equal(a.activity.assets.large_image,'mp:external/fixture')
assert.equal(a.activity.timestamps.start,now-30000)
assert.equal(a.activity.timestamps.end,now+150000)
assert.equal(a.activity.metadata.button_urls[0],'https://music.youtube.com/watch?v=abcdefghijk')
await presence.tick();assert.equal(presence.activity.length,1)
fixture.media.playing=false;await presence.tick();assert.equal(presence.activity.at(-1).activity,null)
await presence.tick();assert.equal(presence.activity.length,2)
fixture.media.playing=true;fixture.media.art='https://i.ytimg.com/image.jpg?token=private';fixture.media.title='New fixture'
await presence.tick();assert(!presence.activity.at(-1).activity.assets)
presence.stop();assert.equal(presence.activity.at(-1).activity,null);assert.equal(presence.timers.size,0)
assert.equal(presence.requests.at(-1),'vexcord.bitchord.stop')
const disabled=load('bitchord-presence',{config:{publish:false},media:fixture.media});await disabled.plugin.start(disabled.api);await disabled.tick();assert.equal(disabled.activity.length,0);assert.equal(disabled.requests.length,0);disabled.stop()
console.log('PASS route eligibility, fallback, reversible hooks, BitChord activity, album art, timestamps, pause, privacy and lifecycle')
