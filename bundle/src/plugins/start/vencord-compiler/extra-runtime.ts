import { createElement as h,cloneElement,isValidElement,memo,useEffect,useState } from 'react'
import { Alert,Linking,ScrollView,Switch,Text,TextInput,View,ToastAndroid } from 'react-native'
import { Design } from '@valenced/discord/design'
import { Clipboard } from '@valenced/externals/react-native-clipboard'
import { Stores } from '@valenced/discord/flux'
import { getJsonStorage,pluginStoragePathFor } from '@valenced/json-storage'
import { getModules } from '@valenced/modules/finders'
import { withName,withProps } from '@valenced/modules/finders/filters'
import { after,before,instead } from '@valenced/patcher'
import type { PluginOptions } from '@valenced/plugins/types'
import { observeMessageActions } from './message-adapter'
import { messageText } from './message-payload'
import { publishCompilerState } from './runtime-status'
import { snowflake } from './context-operations'
import { dateForId,enhanceGifAlt,imageNames,ircRow,latencyEstimate,replyTimestampRow,selectedSnapshot,voiceFile,textAttachment,originalImage,publicImageUrl,
    type ExtraProgram,type MenuProgram,type RowProgram } from './extra-operations'

type AnyRecord=Record<string,any>
type InputManager={getBestActiveInputForChannelId(id:string):{insertText(text:string):void}|undefined}
const messageSheets=new Set(['MessageLongPressActionSheet','LongPressMessageActionSheet'])
const userSheets=new Set(['UserProfileActionSheet','UserProfileStackedActionSheet','VoicePanelUserActionSheet'])
const guildSheets=new Set(['GuildLongPressActionSheet','GuildProfileActionSheet','GuildActionSheet'])
const channelSheets=new Set(['ChannelLongPressActionSheet','ThreadLongPressActionSheet','ForumPostLongPressActionSheet'])
function notify(message:string){try{ToastAndroid.show(message,ToastAndroid.SHORT)}catch{}}
function findInput(manager:InputManager|undefined,channel:unknown){
    if(!snowflake(channel))return
    try{return manager?.getBestActiveInputForChannelId(channel)}catch{return undefined}
}
function observeInputs(cb:(input:InputManager)=>void){
    return getModules(withProps<any>('getBestActiveInputForChannelId'),m=>{
        if(typeof m?.getBestActiveInputForChannelId==='function')cb(m)
    },{max:1,cached:false})
}
function isWebUrl(text:string){
    if(text.length>4000)return false
    try{const u=new URL(text.trim());return ['https:','http:'].includes(u.protocol)&&!u.username&&!u.password}catch{return false}
}
function gifPasteOptions(program:Extract<ExtraProgram,{op:'input.gifPaste'}>):PluginOptions{
    return {start({cleanup,plugin}){
        const expected=['ChatInput','sendMessage'],bound=new Set<string>()
        let live=true,inputs:InputManager|undefined
        const publish=()=>{if(live)publishCompilerState(plugin.manifest.id,expected,[...bound])}
        publish()
        cleanup(()=>{live=false;inputs=undefined;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
        cleanup(observeInputs(m=>{if(live){inputs=m;bound.add('ChatInput');publish()}}))
        cleanup(observeMessageActions(target=>{
            if(!live||bound.has('sendMessage'))return
            try{
                cleanup(instead(target,'sendMessage',function(this:unknown,args:any[],original:any){
                    if(!live||args[3]?.location!==program.location)return Reflect.apply(original,this,args)
                    const text=messageText(args[1])
                    if(!text||!isWebUrl(text))return Reflect.apply(original,this,args)
                    const input=findInput(inputs,args[0])
                    if(!input||typeof input.insertText!=='function'){
                        notify('Kein aktiver Chat-Eingabebereich. GIF wurde nicht gesendet.')
                        return
                    }
                    try{input.insertText(text.trim()+' ')}catch{notify('GIF konnte nicht in den Entwurf eingefügt werden.')}
                    return
                }))
                bound.add('sendMessage');publish()
            }catch(e){plugin.reportError(e)}
        }))
    }}
}
function appendGroup(root:any,group:any):any{
    let budget=250,done=false
    const Group=Design.ActionSheetRow.Group
    const isGroup=(x:any)=>isValidElement(x)&&(x.type===Group||(x.type as any)?.name==='ActionSheetRowGroup')
    function visit(x:any,depth:number):any{
        if(done||depth>16||--budget<0)return x
        if(Array.isArray(x)){
            if(x.some(v=>isValidElement(v)&&v.key===group.key)){done=true;return x}
            if(x.some(isGroup)){done=true;return [...x,group]}
            let changed=false
            const out=x.map(v=>{const n=visit(v,depth+1);changed||=n!==v;return n})
            return changed?out:x
        }
        if(!isValidElement(x))return x
        const children=(x.props as any)?.children
        if(children==null)return x
        const out=visit(children,depth+1)
        return children===out?x:cloneElement(x as any,undefined,out)
    }
    return visit(root,0)
}
type SelectedAction={label:string;run:()=>void}
function displayCopy(title:string,value:string){
    Alert.alert(title,value.slice(0,1800),[{text:'Schließen',style:'cancel'},{text:'Kopieren',onPress:()=>{Clipboard.setString(value);notify('Kopiert')}}])
}
async function copyTextAttachment(file:{url:string;size:number;name:string},maxBytes:number){
    const controller=new AbortController()
    const timer=setTimeout(()=>controller.abort(),10000)
    try{
        const response=await fetch(file.url,{method:'GET',credentials:'omit',redirect:'error',signal:controller.signal})
        if(!response.ok||response.redirected||response.url&&response.url!==file.url)throw Error('Datei nicht verfügbar')
        const length=Number(response.headers.get('content-length'))
        const encoding=response.headers.get('content-encoding')??'identity'
        const contentType=response.headers.get('content-type')??''
        if(!response.headers.has('content-length')||!Number.isFinite(length)||length<0||length>maxBytes||length!==file.size||encoding!=='identity'||
           !/^(?:text\/|application\/(?:json|xml)(?:;|$))/i.test(contentType))throw Error('Datei zu groß oder kein Text')
        const raw=await response.text()
        if(raw.length>maxBytes||raw.includes('\u0000'))throw Error('Kein unterstützter Text')
        Clipboard.setString(raw)
        notify('Dateiinhalt kopiert')
    }catch{notify('Datei konnte nicht vollständig als Text geladen werden.')}
    finally{clearTimeout(timer)}
}
function selectedAction(program:MenuProgram,key:string,p:AnyRecord,inputs?:InputManager):SelectedAction|undefined{
    if(!p||typeof p!=='object')return
    const message=messageSheets.has(key)?p.message:undefined
    const channel=p.channel??p.thread
    if(program.op==='menu.quickMention'){
        const user=message?.author?.id,channelId=message?.channel_id??channel?.id
        if(!snowflake(user)||!snowflake(channelId)||!inputs)return
        return {label:program.label,run:()=>{
            const input=findInput(inputs,channelId)
            if(input&&typeof input.insertText==='function')input.insertText('<@'+user+'> ')
            else notify('Kein aktiver Chat-Eingabebereich.')
        }}
    }
    if(program.op==='menu.voiceFile'){
        const uri=voiceFile(message);if(!uri)return
        return {label:program.label,run:()=>{void Linking.openURL(uri).catch(()=>notify('Sprachdatei konnte nicht geöffnet werden.'))}}
    }
    if(program.op==='menu.imageFilename'){
        const names=imageNames(message);if(!names.length)return
        return {label:program.label,run:()=>displayCopy('Bilddateien',names.join('\n'))}
    }

    if(program.op==='menu.originalImage'){
        const uri=originalImage(message);if(!uri)return
        return {label:program.label,run:()=>{void Linking.openURL(uri).catch(()=>notify('Originalbild konnte nicht geöffnet werden.'))}}
    }
    if(program.op==='menu.copyTextFile'){
        const file=textAttachment(message,program.maxBytes);if(!file)return
        return {label:program.label,run:()=>Alert.alert('Textdatei kopieren?',file.name+' · '+file.size+' Bytes. Die ausgewählte Datei wird von Discord geladen.',[
            {text:'Abbrechen',style:'cancel'},{text:'Laden',onPress:()=>{void copyTextAttachment(file,program.maxBytes)}}])}
    }
    if(program.op==='menu.reverseImage'){
        const url=publicImageUrl(message);if(!url)return
        const engines=program.engines.filter(e=>['https://lens.google.com/uploadbyurl?url=','https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:'].includes(e.base)).slice(0,2)
        if(!engines.length)return
        return {label:program.label,run:()=>Alert.alert('Bildersuche öffnen?', 'Der ausgewählte externe Bildlink wird an die gewählte Suchmaschine übergeben.',[
            {text:'Abbrechen',style:'cancel'},...engines.map(e=>({text:e.name,onPress:()=>{void Linking.openURL(e.base+encodeURIComponent(url)).catch(()=>notify('Suche konnte nicht geöffnet werden.'))}}))])}
    }
    if(program.op==='menu.latency'){
        if(message?.author?.bot)return
        const ms=latencyEstimate(message?.id,message?.nonce)
        if(ms===undefined||Math.abs(ms)<program.threshold*1000)return
        return {label:program.label,run:()=>Alert.alert('Zeitdifferenz',Math.abs(ms/1000).toFixed(2)+' s zwischen Nachrichten-ID und Nonce. '+(ms<0?'Die Nonce liegt zeitlich vorwärts. ':'')+'Das ist keine Messung der Netzwerklatenz.')}
    }
    const guild=guildSheets.has(key)?p.guild:undefined
    if(program.op==='menu.serverInfo'){
        const snap=selectedSnapshot('Guild',guild)
        if(!snap)return
        return {label:program.label,run:()=>displayCopy(String(guild.name??'Server-Info'),Object.entries(snap).map(([k,v])=>k+': '+String(v)).join('\n'))}
    }
    if(program.op==='menu.rawSelection'){
        let snapshot:Record<string,unknown>|undefined
        if(message)snapshot=selectedSnapshot('Message',message)
        else if(userSheets.has(key)&&p.user)snapshot=selectedSnapshot('User',p.user)
        else if(guild)snapshot=selectedSnapshot('Guild',guild)
        else if(channelSheets.has(key)&&channel)snapshot=selectedSnapshot('Channel',channel)
        if(!snapshot)return
        return {label:program.label,run:()=>displayCopy('Ausgewählte Daten · redigiert',JSON.stringify(snapshot,null,2))}
    }
}
function isSheetFor(program:MenuProgram,key:unknown):key is string{
    if(typeof key!=='string')return false
    if(program.op==='menu.serverInfo')return guildSheets.has(key)
    if(program.op==='menu.rawSelection')return messageSheets.has(key)||userSheets.has(key)||guildSheets.has(key)||channelSheets.has(key)
    return messageSheets.has(key)
}
function menuOptions(program:MenuProgram):PluginOptions{
    return {start({cleanup,plugin}){
        let live=true,inputs:InputManager|undefined
        const expected=['ActionSheet.openLazy','SelectionMenu',...(program.op==='menu.quickMention'?['ChatInput']:[])]
        const bound=new Set<string>()
        const publish=()=>{if(live)publishCompilerState(plugin.manifest.id,expected,[...bound])}
        publish()
        cleanup(()=>{live=false;inputs=undefined;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
        if(program.op==='menu.quickMention')cleanup(observeInputs(m=>{if(live){inputs=m;bound.add('ChatInput');publish()}}))
        cleanup(getModules(withProps<any>('openLazy','hideActionSheet','hideAllActionSheets'),actions=>{
            if(!live||bound.has('ActionSheet.openLazy')||typeof actions.openLazy!=='function')return
            try{
                cleanup(before(actions,'openLazy',(args:any[])=>{
                    if(!live)return args
                    const [lazy,key,props]=args
                    if(!isSheetFor(program,key)||!props||typeof lazy?.then!=='function')return args
                    const next=args.slice()
                    next[0]=Promise.resolve(lazy).then(module=>{
                        if(!live)return module
                        const Original=module?.default
                        const isMemo=Original?.$$typeof===Symbol.for('react.memo')
                        const render=isMemo?Original.type:Original
                        if(typeof render!=='function'||render.prototype?.isReactComponent)return module
                        function MobileSelection(p:any){
                            const tree=render(p)
                            if(!live)return tree
                            try{
                                const action=selectedAction(program,key,{...props,...p},inputs)
                                if(!action)return tree
                                const group=h(Design.ActionSheetRow.Group,{key:'vx-'+program.op},
                                    h(Design.ActionSheetRow,{label:action.label,onPress:()=>{
                                        if(!live)return
                                        try{actions.hideActionSheet(key);action.run()}catch{notify('Aktion konnte nicht ausgeführt werden.')}
                                    }}))
                                const changed=appendGroup(tree,group)
                                if(changed!==tree&&!bound.has('SelectionMenu')){
                                    bound.add('SelectionMenu');publish()
                                }
                                return changed
                            }catch{return tree}
                        }
                        return {...module,default:isMemo?memo(MobileSelection):MobileSelection}
                    })
                    return next
                }))
                bound.add('ActionSheet.openLazy');publish()
            }catch(e){plugin.reportError(e)}
        },{max:4,cached:false}))
    }}
}
function rowOptions(program:RowProgram):PluginOptions{
    type IrcSettings={lightness:number;saturation:number;seed:string;onlyUncolored:boolean;onlyDMs:boolean}
    let store:ReturnType<typeof getJsonStorage<IrcSettings>>|undefined
    const initial=program.op==='rows.ircColors'?{lightness:program.lightness,saturation:program.saturation,seed:program.seed,onlyUncolored:program.onlyUncolored,onlyDMs:program.onlyDMs}:{lightness:70,saturation:100,seed:'',onlyUncolored:false,onlyDMs:false}
    const config=()=>({...initial,...store?.cache}) as IrcSettings
    function ColorSettings(){
        const [s,set]=useState(config)
        useEffect(()=>{const off=store?.subscribe(()=>set(config()));return()=>off?.()},[])
        const update=(p:Partial<IrcSettings>)=>{set(old=>({...old,...p}));void store?.set(p)}
        const field=(label:string,value:string,change:(x:string)=>void)=>h(View,{style:{marginBottom:12}},h(Text,{style:{color:'#ddd9e7'}},label),h(TextInput,{value,onChangeText:change,style:{color:'#eee',backgroundColor:'#252632',borderRadius:10,padding:10}}))
        const toggle=(label:string,value:boolean,change:(x:boolean)=>void)=>h(View,{style:{flexDirection:'row',alignItems:'center',paddingVertical:10}},h(Text,{style:{color:'#eee',flex:1}},label),h(Switch,{value,onValueChange:change}))
        return h(ScrollView,{style:{flex:1,backgroundColor:'#101118'},contentContainerStyle:{padding:16}},
            h(Text,{style:{color:'#c8afff',fontSize:20,fontWeight:'700',marginBottom:14}},'IRC Colors'),
            field('Seed',s.seed,v=>update({seed:v.slice(0,64)})),
            field('Helligkeit · 25–85',String(s.lightness),v=>{if(/^\d{1,2}$/.test(v))update({lightness:Math.max(25,Math.min(85,Number(v)))})}),
            field('Sättigung · 0–100',String(s.saturation),v=>{if(/^\d{1,3}$/.test(v))update({saturation:Math.min(100,Number(v))})}),
            toggle('Nur Nutzer ohne Rollenfarbe',s.onlyUncolored,v=>update({onlyUncolored:v})),
            toggle('Nur Direktnachrichten',s.onlyDMs,v=>update({onlyDMs:v})),
            h(Text,{style:{color:'#aaa6bb'}},'Ändert ausschließlich gerenderte Chat-Namen. Neue Zeilen übernehmen die Einstellung; ein erneutes Öffnen des Chats kann nötig sein.'))
    }
    return {
        async start({cleanup,plugin}){
            let live=true
            const expected=[program.op==='rows.gifAlt'?'ChatRows':'RowManager.generate','RowTransformObserved'],bound=new Set<string>()
            const publish=()=>{if(live)publishCompilerState(plugin.manifest.id,expected,[...bound])}
            publish()
            cleanup(()=>{live=false;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
            if(program.op==='rows.ircColors'){
                store=getJsonStorage<IrcSettings>(pluginStoragePathFor(plugin.manifest.id,'compiler-settings.json'),{default:initial})
                await store.get()
                if(!live)return
            }
            function observe<T>(old:T,next:T):T{
                if(next!==old&&!bound.has('RowTransformObserved')){bound.add('RowTransformObserved');publish()}
                return next
            }
            if(program.op==='rows.gifAlt'){
                cleanup(getModules(withProps<any>('updateRows','clearRows','scrollToBottom'),m=>{
                    if(!live||bound.has(expected[0]!))return
                    try{
                        cleanup(before(m,'updateRows',(args:any[])=>{
                            try{
                                const p=args[1]
                                if(!Array.isArray(p?.rows))return args
                                const rows=observe(p.rows,enhanceGifAlt(p.rows))
                                return rows===p.rows?args:[args[0],{...p,rows},...args.slice(2)]
                            }catch{return args}
                        }))
                        bound.add(expected[0]!);publish()
                    }catch(e){plugin.reportError(e)}
                },{max:1,cached:false}))
            }else{
                cleanup(getModules(withName<any>('RowManager'),module=>{
                    if(!live||bound.has(expected[0]!)||typeof module?.prototype?.generate!=='function')return
                    try{
                        cleanup(after(module.prototype,'generate',(row:any)=>{
                            try{
                                if(program.op==='rows.replyTimestamp')return observe(row,replyTimestampRow(row))
                                const p={op:'rows.ircColors' as const,...config()}
                                let dm: boolean|undefined
                                if(p.onlyDMs){
                                    const id=row?.message?.channelId??row?.message?.channel_id
                                    const ch=snowflake(id)?(Stores.ChannelStore as any)?.getChannel?.(id):undefined
                                    dm=ch?typeof ch.isPrivate==='function'?ch.isPrivate():[1,3].includes(ch.type):undefined
                                }
                                return observe(row,ircRow(row,p,dm))
                            }catch{return row}
                        }))
                        bound.add(expected[0]!);publish()
                    }catch(e){plugin.reportError(e)}
                },{max:1,cached:false}))
            }
        },
        stop({plugin}){plugin.requireReload()},
        ...(program.op==='rows.ircColors'?{SettingsComponent:ColorSettings}:{})
    }
}
export function createExtraOptions(program:ExtraProgram):PluginOptions{
    if(program.op==='input.gifPaste')return gifPasteOptions(program)
    if(program.op==='rows.replyTimestamp'||program.op==='rows.gifAlt'||program.op==='rows.ircColors')return rowOptions(program)
    return menuOptions(program)
}
