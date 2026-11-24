import { createElement as h, cloneElement, isValidElement, useEffect, useState } from 'react'
import { View, Text, TextInput, Pressable, Switch, ScrollView } from 'react-native'
import { getJsonStorage, pluginStoragePathFor, type JsonStorage } from '@valenced/json-storage'
import { getModuleWithImportedPath, lookupModuleWithImportedPath } from '@valenced/discord/utils/modules/finders'
import { Clipboard } from '@valenced/externals/react-native-clipboard'
import { after, before } from '@valenced/patcher'
import { getModules } from '@valenced/modules/finders'
import { withName } from '@valenced/modules/finders/filters'
import { onFluxEventDispatched } from '@valenced/discord/flux'
import type { PluginOptions } from '@valenced/plugins/types'
import { publishCompilerState } from './runtime-status'
import { applyReplacementRules, boundedRegex, convertTimeShortcuts, silentPrefix, timestampTag, type MessageProgram, type ReplacementRule } from './operations'

type State={rules:ReplacementRule[];enabled:boolean;autoDisable:boolean;persist:boolean}
const defaults=(p:MessageProgram):State=>({rules:[],enabled:p.op==='message.timestamp'?p.enabledDefault:p.op==='message.replace',autoDisable:p.op==='message.silent'?p.autoDisableDefault:true,persist:p.op==='message.silent'?p.persistDefault:false})
const theme={bg:'#101118',panel:'#1c1d26',text:'#f2eff8',muted:'#b6b2c4',accent:'#c8afff'}
const field={backgroundColor:theme.panel,color:theme.text,padding:11,borderRadius:9,marginVertical:5}
const title=(t:string)=>h(Text,{style:{color:theme.text,fontWeight:'600',fontSize:16,marginTop:10}},t)
const note=(t:string)=>h(Text,{style:{color:theme.muted,fontSize:12,marginVertical:5}},t)
function button(label:string,fn:()=>void){return h(Pressable,{onPress:fn,style:{padding:12,backgroundColor:theme.panel,borderRadius:10,marginVertical:4}},h(Text,{style:{color:theme.accent,fontWeight:'600'}},label))}
function toggle(label:string,value:boolean,onValueChange:(v:boolean)=>void){return h(View,{style:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingVertical:8}},h(Text,{style:{color:theme.text,flex:1}},label),h(Switch,{value,onValueChange}))}
const CHAT_ACTIONS='modules/chat_input/native/action_buttons/ChatInputRightActions.tsx'
const CHAT_BUTTON='modules/chat_input/native/action_buttons/ChatInputActionButton.tsx'
function insertAction(tree:any,element:any,depth=0):any{
    if(depth>14||!isValidElement(tree))return tree
    const props:any=tree.props
    const children=props?.children
    if(Array.isArray(children)){
        if(children.some(c=>isValidElement(c)&&c.key===element.key))return tree
        return cloneElement(tree as any,undefined,[element,...children])
    }
    if(isValidElement(children)){
        const changed=insertAction(children,element,depth+1)
        if(changed!==children)return cloneElement(tree as any,undefined,changed)
    }
    return tree
}

/** Shared runtime for source-derived message programs. Storage is per plugin;
 * only explicit user sends are transformed. No background sends or reads. */
export function createMessageOptions(program:MessageProgram):PluginOptions{
    let storage:JsonStorage<State>|undefined
    let silentArmed=false
    const listeners=new Set<()=>void>()
    const emit=()=>{for(const f of listeners)f()}
    const arm=(v:boolean)=>{silentArmed=v;emit()}
    let binding='Waiting for Messages'
    const getState=()=>({...defaults(program),...(storage?.cache??{})}) as State
    const update=(patch:Partial<State>)=>{if(storage)void storage.set(patch as any)}
    function SettingsComponent(){
        const [state,setState]=useState(getState)
        const [armed,setArmed]=useState(silentArmed)
        const [draft,setDraft]=useState('')
        const [sample,setSample]=useState('')
        const [find,setFind]=useState('')
        const [replace,setReplace]=useState('')
        const [condition,setCondition]=useState('')
        const [regex,setRegex]=useState(false)
        const [format,setFormat]=useState('t')
        useEffect(()=>{
            const cb=()=>{setState(getState());setArmed(silentArmed)}
            listeners.add(cb);const off=storage?.subscribe(cb)
            cb();return()=>{listeners.delete(cb);off?.()}
        },[])
        const input=(value:string,change:(s:string)=>void,placeholder:string)=>h(TextInput,{value,onChangeText:change,placeholder,placeholderTextColor:theme.muted,style:field,autoCapitalize:'none'})
        const rules=Array.isArray(state.rules)?state.rules:[]
        const tag=timestampTag(draft,new Date(),format)
        const preview=program.op==='message.replace'?applyReplacementRules(sample,rules,'',program.skipChannel,program.maxRules):convertTimeShortcuts(sample)
        return h(ScrollView,{style:{flex:1,backgroundColor:theme.bg},contentContainerStyle:{padding:16}},
            title('Mobile Compiler · '+program.op),
            note('Status: '+binding),
            program.op!=='message.silent'?toggle('Nachrichten-Transformation aktiv',state.enabled,v=>update({enabled:v})):null,
            program.op==='message.replace'?h(View,null,
                title('Ersetzungsregeln'),
                note('Lokale Regeln, keine Cloud. Regex unterstützt nur begrenzte Muster ohne Gruppen oder Quantifizierer.'),
                ...rules.map((r,i)=>h(View,{key:i,style:{backgroundColor:theme.panel,padding:10,borderRadius:10,marginVertical:4}},
                    note((r.regex?'Regex: ':'Text: ')+r.find+' → '+r.replace),
                    button('Entfernen',()=>update({rules:rules.filter((_,j)=>i!==j)})))),
                input(find,setFind,'Suchen'),input(replace,setReplace,'Ersetzen'),input(condition,setCondition,'Nur wenn Text enthält (optional)'),
                toggle('Begrenzter Regex',regex,setRegex),
                button('Regel hinzufügen',()=>{
                    if(!find||find.length>96||replace.length>256||condition.length>128||rules.length>=program.maxRules||(regex&&!boundedRegex(find)))return
                    update({rules:[...rules,{find,replace,onlyIfIncludes:condition,regex}]});setFind('');setReplace('');setCondition('')
                }),
                input(sample,setSample,'Testnachricht'),note('Vorschau: '+preview)
            ):program.op==='message.timestamp'?h(View,null,
                note('Schreibe eine Uhrzeit in einfachen Backticks, z. B. '+String.fromCharCode(96)+'18:30'+String.fromCharCode(96)+'. Vergangene Uhrzeiten werden auf morgen gesetzt.'),
                input(draft,setDraft,'HH:MM oder 8:30 PM'),input(format,setFormat,'Discord-Format: t, T, d, D, f, F, R'),
                note(tag??'Gültige Uhrzeit eingeben'),button('Zeitstempel kopieren',()=>{if(tag)Clipboard.setString(tag)}),
                input(sample,setSample,'Testnachricht'),note('Vorschau: '+preview)
            ):h(View,null,
                toggle('Stille Nachricht bereit',armed,arm),
                toggle('Nach einer Nachricht deaktivieren',state.autoDisable,v=>update({autoDisable:v})),
                toggle('Bei Kanalwechsel beibehalten',state.persist,v=>update({persist:v})),
                note('Der Chat-Button schaltet den Modus um. Bei deaktiviertem Auto-Reset bleibt er bis zum Ausschalten aktiv. Es werden nur manuell gesendete Nachrichten verändert.')
            )
        )
    }
    function ChatButton(){
        const [armed,setArmed]=useState(silentArmed)
        const [Action,setAction]=useState<any>(()=>lookupModuleWithImportedPath<any>(CHAT_BUTTON)[0]?.default)
        useEffect(()=>{
            const cb=()=>setArmed(silentArmed);listeners.add(cb)
            const off=Action?undefined:getModuleWithImportedPath<any>(CHAT_BUTTON,m=>setAction(()=>m.default))
            return()=>{listeners.delete(cb);off?.()}
        },[Action])
        if(!Action)return null
        const Icon=()=>h(Text,{style:{color:armed?theme.accent:theme.muted,fontSize:18,fontWeight:'700'}},armed?'S':'s')
        return h(Action,{accessibilityLabel:armed?'Stille Nachricht aktiv':'Stille Nachricht aus',accessibilityState:{checked:armed},IconComponent:Icon,onPress:()=>arm(!silentArmed)})
    }
    return {
        async start({cleanup,plugin}){
            const expected=program.op==='message.silent'?['sendMessage','ChatInputRightActions']:['sendMessage']
            const bound=new Set<string>()
            let alive=true
            const publish=()=>{if(alive)publishCompilerState(plugin.manifest.id,expected,[...bound])}
            const mark=(name:string)=>{bound.add(name);publish()}
            publish()
            storage=getJsonStorage<State>(pluginStoragePathFor(plugin.manifest.id,'compiler-settings.json'),{default:defaults(program)})
            await storage.get()
            silentArmed=false;binding='Waiting for Messages'
            cleanup(()=>{alive=false;arm(false);binding='Stopped';publishCompilerState(plugin.manifest.id,[],[],'stopped')})
            cleanup(getModules(withName<any>('Messages'),namespace=>{
                if(!alive||bound.has('sendMessage'))return
                const target=(namespace as any)?.default??namespace
                if(typeof target?.sendMessage!=='function'){binding='Messages.sendMessage unavailable';emit();return}
                try{
                    cleanup(before(target,'sendMessage',(args:any[])=>{
                        try{
                            const state=getState()
                            const content=args[1]
                            if(typeof content!=='string')return args
                            let result=content
                            if(program.op==='message.replace'&&state.enabled)
                                result=applyReplacementRules(content,state.rules,String(args[0]??''),program.skipChannel,program.maxRules)
                            else if(program.op==='message.timestamp'&&state.enabled)result=convertTimeShortcuts(content)
                            else if(program.op==='message.silent'&&silentArmed){
                                result=silentPrefix(content,program.prefix)
                                if(content.trim()&&state.autoDisable)arm(false)
                            }
                            if(result===content)return args
                            const next=args.slice();next[1]=result;return next
                        }catch{return args}
                    }))
                    binding='Messages.sendMessage bound';mark('sendMessage');emit()
                    nativeLoggingHook('[ValancedX Compiler] '+program.op+' bound sendMessage',1)
                }catch(e){binding='Hook failed';emit();plugin.reportError(e)}
            },{max:1,cached:false,returnNamespace:true}))
            if(program.op==='message.silent'){
                cleanup(onFluxEventDispatched('CHANNEL_SELECT' as any,payload=>{if(!getState().persist)arm(false);return payload}))
                cleanup(getModuleWithImportedPath<any>(CHAT_ACTIONS,module=>{
                    if(!alive||bound.has('ChatInputRightActions'))return
                    const component=module.default
                    if(typeof component?.type!=='function')return
                    try{
                        const element=h(ChatButton,{key:'vx-compiler-silent'})
                        cleanup(after(component,'type',(tree:any)=>insertAction(tree,element)))
                        mark('ChatInputRightActions')
                        nativeLoggingHook('[ValancedX Compiler] message.silent bound ChatInputRightActions',1)
                    }catch(e){plugin.reportError(e)}
                }))
            }
        },
        SettingsComponent,
    }
}
