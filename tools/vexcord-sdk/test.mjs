import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url)
const ts=process.env.VX_TYPESCRIPT?require(process.env.VX_TYPESCRIPT):globalThis.Deno?await import('npm:typescript@5.9.3'):require('typescript')
const root=path.resolve('bundle/lib/plugins/src/vexcord')
function load(name,deps={}){
 const source=fs.readFileSync(path.join(root,name),'utf8')
 const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},reportDiagnostics:true})
 assert.equal(result.diagnostics.length,0,name)
 const mod={exports:{}}
 new Function('module','exports','require',result.outputText)(mod,mod.exports,id=>{if(!(id in deps))throw Error(id);return deps[id]})
 return mod.exports
}
const {createVexcordScope,storageFile,transformMessageArgs}=load('scope.ts')
const cleanups=[],errors=[],observers=[],events=new Map()
let sent,edited
const record={content:'hello',nonce:'fixture',attachments:[{id:'1'}]}
const messages={sendMessage(...a){sent=a},receiveMessage(){},editMessage(...a){edited=a}}
const target={sum(a,b){return a+b}}
const disposals=[]
const fake={
 observe(keys,cb){assert(keys.length>0);const entry={keys,cb,active:true};observers.push(entry);if(keys.every(k=>k in messages))cb(messages);return()=>{entry.active=false}},
 before(o,k,fn){const original=o[k];o[k]=function(...a){return original.apply(this,fn(a))};return()=>{o[k]=original;disposals.push(k)}},
 after(o,k,fn){const original=o[k];o[k]=function(...a){return fn(original.apply(this,a))};return()=>{o[k]=original}},
 event(name,cb){events.set(name,cb);return()=>events.delete(name)},
 storage(file,defaults){assert.equal(file,'vexcord-settings.json');let value={...defaults};const listeners=new Set();return {async get(){return value},async set(p){value={...value,...p};for(const cb of listeners)cb(value)},async replace(p){value={...p};for(const cb of listeners)cb(value)},subscribe(cb){listeners.add(cb);return()=>listeners.delete(cb)}}},
 cleanup(fn){cleanups.push(fn)},error(op){errors.push(op)}
}
const api=createVexcordScope('example.vexcord.hello',fake)
assert.equal(api.apiVersion,1)
assert.equal(storageFile('settings'),'vexcord-settings.json')
for(const n of ['../x','/absolute','a/b','a\\b','', 'a.json'])assert.throws(()=>storageFile(n))
const off=api.messages.transformOutgoing(s=>s+'!')
api.messages.transformEdit(s=>s.toUpperCase())
messages.sendMessage('c',record,true);assert.equal(sent[1].content,'hello!');assert.equal(sent[1].attachments,record.attachments);assert.equal(record.content,'hello')
messages.editMessage('c','id',record);assert.equal(edited[2].content,'HELLO')
messages.sendMessage('c','hello');assert.equal(sent[1],'hello!')
off();off();messages.sendMessage('c',record);assert.equal(sent[1],record)
assert.equal(disposals.filter(k=>k==='sendMessage').length,1)
const before=api.hooks.before(target,'sum',a=>[a[0]*2,a[1]])
const after=api.hooks.after(target,'sum',r=>r+1)
assert.equal(target.sum(2,3),8)
after();before();assert.equal(target.sum(2,3),5)
let observed
api.events.observe('TEST_EVENT',p=>observed=p)
events.get('TEST_EVENT')({fixture:1});assert.deepEqual(observed,{fixture:1})
const store=api.storage.open('settings',{enabled:true})
let updated;store.subscribe(v=>updated=v)
await store.set({enabled:false});assert.equal((await store.get()).enabled,false);assert.equal(updated.enabled,false)
await store.replace({enabled:true});assert.equal((await store.get()).enabled,true)
const identity=['c',record];assert.equal(transformMessageArgs(identity,1,s=>s),identity)
const getter=Object.defineProperty({},'content',{get(){throw Error('getter')}})
assert.equal(transformMessageArgs(['c',getter],1,s=>s+'!')[1],getter)
let disposed=0;api.onCleanup(()=>disposed++)
for(const fn of cleanups.reverse())fn()
assert.equal(disposed,1);assert.equal(events.size,0)
messages.editMessage('c','id',record);assert.equal(edited[2],record)
assert.throws(()=>api.hooks.before(target,'sum',()=>[]))
assert.throws(()=>store.get())
assert.equal(errors.length,0)
console.log('PASS Vexcord SDK: automatic cleanup, scoped storage, structural message hooks and Flux observation')

// Test actual runtime root and loader compatibility with mocked native services.
const rootRuntime=load('runtime.ts',{
 '@valenced/modules/native':{callNativeMethodSync(name,args){assert.equal(name,'vexcord.runtime.info');assert.deepEqual(args,[]);return {name:'Vexcord',apiVersion:1,version:'1.0.27',platform:'android',compatibility:'valenced-v1'}}},
 '@valenced/json-storage':{},'./scope':{createVexcordScope}
})
const {vexcordRoot}=rootRuntime
assert.equal(vexcordRoot.runtimeInfo().name,'Vexcord')
assert.throws(()=>vexcordRoot.definePlugin({start:'bad'}))
const old={start(){}}
assert.equal(vexcordRoot.definePlugin(old),old)
const script=fs.readFileSync('bundle/lib/plugins/src/_internal/script.ts','utf8')
const transformed=ts.transpileModule(script,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},reportDiagnostics:true})
assert.equal(transformed.diagnostics.length,0)
const mod={exports:{}}
new Function('module','exports','require',transformed.outputText)(mod,mod.exports,id=>({
 './legacy-adapter':{createLegacyOptionsFactory:()=>()=>({legacy:true})},
 '../apis':{pUnscopedApi:{fixture:true}},
 '../vexcord/runtime':{vexcordRoot}
})[id])
const loadPlugin=mod.exports.createOptionsFactory
assert.equal(loadPlugin('old.plugin','({default:plugin({start(){return valenced.fixture}})})')().start(),true)
assert.equal(loadPlugin('veyra.legacy.fixture','legacy')().legacy,true)
const sample=loadPlugin('example.vexcord.hello',fs.readFileSync('sdk/example/index.js','utf8'))()
let output
sample.start({vexcord:{messages:{transformOutgoing(fn){output=fn('[demo]')}}}})
assert.equal(output,'Vexcord')
console.log('PASS Vexcord and legacy Valenced bundle loader compatibility')
