import { createElement as h, cloneElement, isValidElement, memo, useEffect, useState } from 'react'
import { ScrollView, Switch, Text, TextInput, View } from 'react-native'
import { Design } from '@valenced/discord/design'
import { Clipboard } from '@valenced/externals/react-native-clipboard'
import { Stores, onFluxEventDispatched } from '@valenced/discord/flux'
import { getJsonStorage, pluginStoragePathFor } from '@valenced/json-storage'
import { getModules } from '@valenced/modules/finders'
import { withProps } from '@valenced/modules/finders/filters'
import { before } from '@valenced/patcher'
import { publishCompilerState } from './runtime-status'
import { emojiMarkdown, replyMentionPolicy, snowflake, userProfileLink, type ContextProgram } from './context-operations'
import type { PluginOptions } from '@valenced/plugins/types'

type Props=Record<string,any>
const userSheets=new Set(['UserProfileActionSheet','UserProfileStackedActionSheet','VoicePanelUserActionSheet'])
const emojiSheets=new Set(['EmojiActionSheet','EmojiOptionsActionSheet','EmojiOverflowActionSheet','EmojiPickerActionSheet','MessageEmojiActionSheet','ReactionEmojiOptionsActionSheet','MessageCustomEmojiActionSheet'])
const purple='#c8afff',text='#f2eff8',panel='#1c1d26'
function menuValue(program:ContextProgram,p:Props):string|undefined{
    if(program.op==='menu.userUrl')return userProfileLink(p.user?.id??p.userId)
    if(program.op==='menu.emojiMarkdown'){
        const e=p.emoji??p.customEmoji??p.expression
        return emojiMarkdown(e?.id??e?.emojiId??p.emojiId,e?.name??e?.emojiName??p.emojiName,e?.animated===true||e?.isAnimated===true||p.animated===true)
    }
}
/** Search only actual ActionSheetRow groups. Never traverse arbitrary props,
 * modify existing rows, call user callbacks during render or insert twice. */
function appendRow(root:any,group:any):any{
    let budget=200,done=false
    const Group=Design.ActionSheetRow.Group
    const isGroup=(v:any)=>isValidElement(v)&&(v.type===Group||(v.type as any)?.name==='ActionSheetRowGroup')
    function visit(v:any,depth:number):any{
        if(done||depth>14||--budget<0)return v
        if(Array.isArray(v)){
            if(v.some(x=>isValidElement(x)&&x.key===group.key)){done=true;return v}
            if(v.some(isGroup)){done=true;return [...v,group]}
            let changed=false
            const result=v.map(x=>{const next=visit(x,depth+1);if(next!==x)changed=true;return next})
            return changed?result:v
        }
        if(!isValidElement(v))return v
        const props:any=v.props,children=props?.children
        if(children==null)return v
        const next=visit(children,depth+1)
        return next===children?v:cloneElement(v as any,undefined,next)
    }
    return visit(root,0)
}

function createMenuOptions(program:Extract<ContextProgram,{op:'menu.userUrl'|'menu.emojiMarkdown'}>):PluginOptions{
    return {
        start({cleanup,plugin}){
            let live=true
            const expected=['ActionSheet.openLazy',program.op==='menu.userUrl'?'UserProfileMenu':'EmojiMenu']
            const bound=new Set<string>()
            const publish=()=>{if(live)publishCompilerState(plugin.manifest.id,expected,[...bound])}
            publish()
            cleanup(()=>{live=false;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
            cleanup(getModules(withProps<any>('openLazy','hideActionSheet','hideAllActionSheets'),actions=>{
                if(!live||bound.has(expected[0]!)||typeof actions.openLazy!=='function'||typeof actions.hideActionSheet!=='function')return
                try{
                    cleanup(before(actions,'openLazy',(args:any[])=>{
                        const [lazy,key,props]=args
                        const keys=program.op==='menu.userUrl'?userSheets:emojiSheets
                        if(!live||typeof key!=='string'||!keys.has(key)||!props||typeof lazy?.then!=='function')return args
                        const value=menuValue(program,props)
                        if(!value)return args
                        const next=args.slice()
                        // Wrap the component per explicit sheet opening. Do not
                        // replace module exports globally or mutate shared trees.
                        next[0]=Promise.resolve(lazy).then((module:any)=>{
                            if(!live)return module
                            const Original=module?.default
                            const isMemo=Original?.$$typeof===Symbol.for('react.memo')
                            const render=isMemo?Original.type:Original
                            if(typeof render!=='function'||render.prototype?.isReactComponent)return module
                            function MobileSheet(p:any){
                                const tree=render(p)
                                if(!live)return tree
                                try{
                                    const current=menuValue(program,p)??value
                                    const group=h(Design.ActionSheetRow.Group,{key:'vx-'+program.op},
                                        h(Design.ActionSheetRow,{label:program.label,onPress:()=>{
                                            if(!live)return
                                            Clipboard.setString(current)
                                            actions.hideActionSheet(key)
                                        }}))
                                    const changed=appendRow(tree,group)
                                    if(changed!==tree&&!bound.has(expected[1]!)){
                                        bound.add(expected[1]!);publish()
                                        nativeLoggingHook('[ValancedX Compiler] '+program.op+' mobile menu rendered',1)
                                    }
                                    return changed
                                }catch{return tree}
                            }
                            return {...module,default:isMemo?memo(MobileSheet):MobileSheet}
                        })
                        return next
                    }))
                    bound.add(expected[0]!);publish()
                }catch(e){plugin.reportError(e)}
            },{max:4,cached:false}))
        },
    }
}
function createReplyOptions(program:Extract<ContextProgram,{op:'reply.mentionPolicy'}>):PluginOptions{
    type Settings={enabled:boolean;users:string;roles:string;pingListed:boolean}
    const defaults:Settings={enabled:true,users:'',roles:'',pingListed:program.pingListedDefault}
    let store:ReturnType<typeof getJsonStorage<Settings>>|undefined
    const read=()=>({...defaults,...store?.cache}) as Settings
    function SettingsComponent(){
        const [s,set]=useState(read)
        useEffect(()=>{const off=store?.subscribe(()=>set(read()));return()=>off?.()},[])
        const update=(patch:Partial<Settings>)=>{set(previous=>({...previous,...patch}));void store?.set(patch)}
        const toggle=(label:string,v:boolean,fn:(v:boolean)=>void)=>h(View,{style:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingVertical:10}},h(Text,{style:{color:text,flex:1}},label),h(Switch,{value:v,onValueChange:fn}))
        const field=(value:string,fn:(s:string)=>void,placeholder:string)=>h(TextInput,{value,onChangeText:(v:string)=>fn(v.slice(0,4096)),placeholder,placeholderTextColor:'#aaa6bb',multiline:true,autoCapitalize:'none',style:{backgroundColor:panel,color:text,padding:12,borderRadius:10,marginVertical:6}})
        return h(ScrollView,{style:{flex:1,backgroundColor:'#101118'},contentContainerStyle:{padding:16}},
            h(Text,{style:{color:purple,fontSize:20,fontWeight:'700'}},'NoReplyMention'),
            h(Text,{style:{color:text,marginVertical:12}},'Standard-Erwähnung beim Erstellen einer Antwort. Die Discord-Schaltfläche zum manuellen Umschalten bleibt unverändert.'),
            toggle('Aktiv',s.enabled,enabled=>update({enabled})),
            toggle('Nur gelistete Nutzer/Rollen erwähnen',s.pingListed,pingListed=>update({pingListed})),
            field(s.users,users=>update({users}),'Benutzer-IDs, mit Komma getrennt'),
            field(s.roles,roles=>update({roles}),'Rollen-IDs, mit Komma getrennt'))
    }
    return {
        async start({cleanup,plugin}){
            let live=true
            const expected=['ReplyCreate','ReplyEvent']
            const bound=new Set<string>()
            const publish=()=>{if(live)publishCompilerState(plugin.manifest.id,expected,[...bound])}
            publish()
            cleanup(()=>{live=false;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
            store=getJsonStorage<Settings>(pluginStoragePathFor(plugin.manifest.id,'compiler-settings.json'),{default:defaults})
            await store.get()
            if(!live)return
            cleanup(onFluxEventDispatched('CREATE_PENDING_REPLY' as any,(event:any)=>{
                if(!live||typeof event?.shouldMention!=='boolean')return event
                try{
                    if(!bound.has('ReplyEvent')){bound.add('ReplyEvent');publish()}
                    const settings=read()
                    if(!settings.enabled)return event
                    const message=event.message
                    const author=message?.author?.id
                    const channelId=event.channelId??event.channel?.id??message?.channel_id
                    let roles:unknown=[]
                    if(settings.roles&&snowflake(author)&&snowflake(channelId)){
                        const channel=(Stores.ChannelStore as any)?.getChannel?.(channelId)
                        const guild=channel?.guild_id
                        if(snowflake(guild))roles=(Stores.GuildMemberStore as any)?.getMember?.(guild,author)?.roles??[]
                    }
                    const shouldMention=replyMentionPolicy(event.shouldMention,author,roles,settings.users,settings.roles,settings.pingListed)
                    return shouldMention===event.shouldMention?event:{...event,shouldMention}
                }catch{return event}
            }))
            bound.add('ReplyCreate');publish()
        },
        SettingsComponent,
    }
}
export function createContextOptions(program:ContextProgram):PluginOptions{
    return program.op==='reply.mentionPolicy'?createReplyOptions(program):createMenuOptions(program)
}
