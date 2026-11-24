/** Synthetic runtime integration suite. Never contacts Discord or the network. */
import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
const require=createRequire(import.meta.url),ts=require(process.env.VX_TYPESCRIPT||'typescript')
const root=path.resolve('bundle/src/plugins/start/vencord-compiler')
const catalog=JSON.parse(fs.readFileSync('android/src/main/assets/vencord-catalog.json','utf8'))
const snow=t=>((BigInt(t)-1420070400000n)<<22n).toString()
const channel=snow(1600000000000),author=snow(1600000010000),id=snow(1700000003000),nonce=snow(1700000000000)
const url='https://cdn.discordapp.com/attachments/123/456/'
const payload={id,nonce,channel_id:channel,author:{id:author,username:'Fixture',email:'private'},content:'fixture'}
let calls=[],inputAvailable=true,opened=[],inserted=[],copied=[],alerts=[],statuses=[],toasts=[],store
const input={getBestActiveInputForChannelId:c=>c===channel&&inputAvailable?{insertText:t=>inserted.push(t)}:undefined}
const messages={sendMessage(...args){calls.push(args);return 'sent'},receiveMessage(){},editMessage(){}}
const actions={openLazy(...args){this.last=args},hideActionSheet(){},hideAllActionSheets(){}}
const chatRows={updateRows(...args){this.last=args},clearRows(){},scrollToBottom(){}}
function RowManager(){}
RowManager.prototype.generate=function(row){return row}
const modules=[input,messages,actions,chatRows,RowManager]
const patcher={
 before(o,k,fn){const orig=o[k];o[k]=function(...a){return orig.apply(this,fn(a))};return()=>{o[k]=orig}},
 after(o,k,fn){const orig=o[k];o[k]=function(...a){return fn(orig.apply(this,a))};return()=>{o[k]=orig}},
 instead(o,k,fn){const orig=o[k];o[k]=function(...a){return fn.call(this,a,orig)};return()=>{o[k]=orig}},
}
const h=(type,props,...c)=>({type,key:props?.key??null,props:{...props,children:c.length===1?c[0]:c}})
const clone=(v,p,...c)=>h(v.type,{...v.props,...p,key:v.key},...c)
const Row=()=>null,Group=()=>null
Row.Group=Group
const mock={
 'react':{createElement:h,cloneElement:clone,isValidElement:x=>x&&typeof x==='object'&&!!x.type,memo:x=>x,useEffect(){},useState(){}},
 'react-native':{Alert:{alert:(title,message,buttons=[])=>alerts.push({title,message,buttons})},Linking:{openURL:async v=>{opened.push(v)}},ToastAndroid:{SHORT:0,show:v=>toasts.push(v)}},
 '@valenced/discord/design':{Design:{ActionSheetRow:Row}},
 '@valenced/externals/react-native-clipboard':{Clipboard:{setString:v=>copied.push(v)}},
 '@valenced/discord/flux':{Stores:{}},
 '@valenced/json-storage':{getJsonStorage:(_p,o)=>(store={cache:{...o.default},async get(){return this.cache},async set(v){Object.assign(this.cache,v)},subscribe:()=>()=>{}}),pluginStoragePathFor:id=>id},
 '@valenced/modules/finders':{getModules:(f,cb)=>{for(const m of modules.filter(m=>f.name?m.name===f.name:f.props.every(k=>k in m)))cb(m);return()=>{}}},
 '@valenced/modules/finders/filters':{withName:name=>({name}),withProps:(...props)=>({props})},
 '@valenced/patcher':patcher,
 './runtime-status':{publishCompilerState:(...args)=>statuses.push(args)},
}
const loaded=new Map()
function load(file){
 if(loaded.has(file))return loaded.get(file)
 const source=fs.readFileSync(path.join(root,file),'utf8'),out=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},reportDiagnostics:true})
 assert.equal(out.diagnostics.length,0,file)
 const mod={exports:{}}
 new Function('require','module','exports',out.outputText)(id=>id in mock?mock[id]:id.startsWith('./')?load(id.slice(2)+'.ts'):(()=>{throw Error(id)})(),mod,mod.exports)
 loaded.set(file,mod.exports);return mod.exports
}
globalThis.nativeLoggingHook=()=>{}
const {createExtraOptions}=load('extra-runtime.ts')
const rules=Object.fromEntries(catalog.plugins.filter(p=>p.translation.kind==='compiled').map(p=>[p.id,p.translation.program]))
async function run(name,test){
 const program=rules[name],clean=[],errors=[]
 assert(program,name)
 calls=[];opened=[];inserted=[];copied=[];alerts=[];statuses=[];toasts=[];inputAvailable=true
 const opts=createExtraOptions(program),api={cleanup:(...f)=>clean.push(...f),plugin:{manifest:{id:'test.'+name},reportError:e=>errors.push(e),requireReload(){}}}
 try{
  await opts.start(api)
  await test(program)
  assert.equal(errors.length,0,JSON.stringify(errors))
  assert.equal(statuses.at(-1)[2].length,statuses.at(-1)[1].length,'expected hook/render evidence')
 }finally{opts.stop?.(api);for(const f of clean.reverse())f()}
 assert.equal(statuses.at(-1)[3],'stopped')
 console.log('PASS',name)
}
async function select(key,props){
 const original=()=>h('root',null,[h(Group,{key:'original'},h(Row,{label:'Original'}))])
 const source={default:original}
 actions.openLazy(Promise.resolve(source),key,props)
 const sheet=await actions.last[0]
 const tree=sheet.default(props)
 assert.equal(original().props.children.length,1)
 assert.equal(tree.props.children.length,2)
 tree.props.children[1].props.children.props.onPress()
}
await run('gifPaste',async()=>{
 const record={content:'https://images.example.com/happy.gif'}
 assert.equal(messages.sendMessage(channel,record,true,{location:'chat'}),'sent')
 messages.sendMessage(channel,record,true,{location:'gif_reply'})
 assert.deepEqual(inserted,[record.content+' '])
 assert.equal(calls.length,1)
 inputAvailable=false
 messages.sendMessage(channel,record,true,{location:'gif_reply'})
 assert.equal(calls.length,1)
 assert.equal(toasts.length,1)
})
await run('quickMention',async()=>{await select('MessageLongPressActionSheet',{message:payload,channel:{id:channel}});assert.deepEqual(inserted,['<@'+author+'> '])})
await run('voiceDownload',async()=>{const link=url+'voice.ogg?hm=x';await select('MessageLongPressActionSheet',{message:{...payload,flags:8192,attachments:[{url:link,content_type:'audio/ogg'}]}});assert.deepEqual(opened,[link])})
await run('imageFilename',async()=>{await select('MessageLongPressActionSheet',{message:{...payload,attachments:[{url:url+'image.png',content_type:'image/png'}]}});assert.equal(alerts.at(-1).message,'image.png')})
await run('viewRaw',async()=>{await select('MessageLongPressActionSheet',{message:payload});assert(alerts.at(-1).message.includes('fixture'));assert(!alerts.at(-1).message.includes('private'));alerts.at(-1).buttons[1].onPress();assert(!copied[0].includes('email'))})
await run('serverInfo',async()=>{await select('GuildProfileActionSheet',{guild:{id:channel,name:'Fixture',member_count:123}});assert(alerts.at(-1).message.includes('member_count: 123'))})
await run('messageLatency',async()=>{await select('MessageLongPressActionSheet',{message:payload});assert(alerts.at(-1).message.includes('3.00 s'))})
await run('fixImagesQuality',async()=>{const link=url+'image.png?hm=x';await select('MessageLongPressActionSheet',{message:{...payload,attachments:[{url:link,content_type:'image/png'}]}});assert.deepEqual(opened,[link])})
await run('copyFileContents',async()=>{
 const old=globalThis.fetch,link=url+'code.txt?hm=x'
 globalThis.fetch=async(input,options)=>{assert.equal(input,link);assert.equal(options.credentials,'omit');return {ok:true,redirected:false,url:link,headers:new Headers({'content-length':'7','content-type':'text/plain'}),text:async()=> 'fixture'}}
 try{
  await select('MessageLongPressActionSheet',{message:{...payload,attachments:[{url:link,filename:'code.txt',content_type:'text/plain',size:7}]}})
  assert.equal(copied.length,0);alerts.at(-1).buttons[1].onPress()
  await new Promise(r=>setImmediate(r));assert.deepEqual(copied,['fixture'])
 }finally{globalThis.fetch=old}
})
await run('reverseImageSearch',async()=>{
 const img='https://images.example.com/photo.png'
 await select('MessageLongPressActionSheet',{message:{...payload,embeds:[{type:'image',url:img}]}})
 assert.equal(opened.length,0);alerts.at(-1).buttons[1].onPress();assert(opened[0].endsWith(encodeURIComponent(img)))
})
await run('replyTimestamp',async()=>{
 const row={type:1,message:{id,referencedMessage:{message:{id:nonce,username:'Fixture'}}}}
 const out=new RowManager().generate(row);assert.match(out.message.referencedMessage.message.username,/^Fixture \[/);assert.equal(row.message.referencedMessage.message.username,'Fixture')
})
await run('ircColors',async()=>{
 const row={type:1,message:{authorId:author,username:'Fixture'}}
 const out=new RowManager().generate(row);assert.match(out.message.colorString,/^#[a-f0-9]{6}$/);assert(!('colorString'in row.message))
})
await run('betterGifAltText',async()=>{
 const rows=[{alt:'GIF',src:'https://images.example.com/happy-cat.gif'}]
 chatRows.updateRows(null,{rows})
 assert.equal(chatRows.last[1].rows[0].alt,'GIF - happy cat');assert.equal(rows[0].alt,'GIF')
})
console.log('PASS all 13 new generated program lifecycle fixtures')
