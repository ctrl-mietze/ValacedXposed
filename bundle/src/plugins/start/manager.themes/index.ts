import { callNativeMethodSync, registerManagerThemeWake } from '@valenced/modules/native'
import { JsonStorage, pluginStoragePathFor } from '@valenced/json-storage'
import { pList, registerInternalPlugin } from '@valenced/plugins/_'
import { getModules } from '@valenced/modules/finders'
import { withProps } from '@valenced/modules/finders/filters'
import { after } from '@valenced/patcher'
import { Children, Fragment, createElement, cloneElement, isValidElement, useEffect, useRef } from 'react'
import { Animated, AppState, Easing, AccessibilityInfo, StyleSheet } from 'react-native'
import manifest from './manifest.json'
import { palettes, ownTheme, previewPalette, previewTokens } from './palettes'
import { motionPresets, motionById, motionIdFromUri, ambientSource, ambientDuration, ambientOpacity } from './motion-presets'
const allPalettes=[...palettes,...motionPresets.map(p=>p.palette)]
const renderedMotion=new Set<string>()

type ThemeData = { name: string; description: string; colors: any; background?: any }
type FontData = {name:string; description:string; files:Record<string,string>}
const ID='veyra.manager.themes'
const GROUP='imports'
const OWN='veyra'
const hex=/^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i
let storage:any
let imports:Record<string,ThemeData>={}
let fonts:Record<string,FontData>={}
let loadPromise:Promise<void>|undefined
let registeredStore:any
let motionCleanup:(()=>void)|undefined
let polling=false

function themeStore():any {
    return (pList.get('valenced.settings.themes')?.api as any)?.themesInternal?.store
}
function engineError():string {
    const settings=pList.get('valenced.settings.themes')
    const api=pList.get('valenced.api.themes')
    return 'Theme Engine nicht bereit. '+[api,settings].filter(Boolean).map(p=>
        p!.manifest.name+': '+((p as any).errors?.map((e:any)=>String(e?.message??e)).join('; ')||'Neustart/Abhängigkeiten prüfen')).join(' · ').slice(0,600)
}
async function load(){
    if(!loadPromise)loadPromise=(async()=>{
        storage=new (JsonStorage as any)(pluginStoragePathFor(ID,'imports.json'),{default:{imports:{},fonts:{}}})
        const data=await storage.get()??{}
        imports=data.imports??{};fonts=data.fonts??{}
    })().catch(e=>{loadPromise=undefined;throw e})
    await loadPromise
}
function normalizedColor(v:unknown):string|undefined{
    if(typeof v!=='string')return
    if(hex.test(v))return v.length===4?'#'+[...v.slice(1)].map(x=>x+x).join(''):v
    const match=v.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,[^)]+)?\s*\)$/i)
    if(match)return '#'+match.slice(1,4).map(x=>Math.min(255,Number(x)).toString(16).padStart(2,'0')).join('')
}
function parseLegacyTheme(data:any):ThemeData{
    if(!data||typeof data!=='object'||data.spec!==2||typeof data.name!=='string')throw new Error('Nur Spec-2-Themes.')
    const dark:Record<string,string>={},light:Record<string,string>={},midnight:Record<string,string>={},raw:Record<string,string>={}
    const valid=(k:string)=>/^[A-Za-z0-9_-]{1,100}$/.test(k)&&!['__proto__','constructor','prototype'].includes(k)
    for(const [k,v] of Object.entries(data.semanticColors??{}).slice(0,1500)){
        if(!valid(k)||!Array.isArray(v))continue
        const d=normalizedColor(v[0]),l=normalizedColor(v[1]),m=normalizedColor(v[2])??d
        if(d)dark[k]=d;if(l)light[k]=l;if(m)midnight[k]=m
    }
    for(const [k,v] of Object.entries(data.rawColors??{}).slice(0,1500)){
        const c=normalizedColor(v);if(valid(k)&&c)raw[k]=c
    }
    if(!Object.keys(dark).length&&!Object.keys(light).length&&!Object.keys(raw).length)throw new Error('Keine gültigen Farben.')
    const aliases:Record<string,string[]>={BACKGROUND_PRIMARY:['BACKGROUND_BASE_LOWER','BG_BASE_PRIMARY'],
        BACKGROUND_SECONDARY:['BACKGROUND_BASE_LOW','BACKGROUND_BASE_LOWEST','CHANNEL_BACKGROUND_DEFAULT','PANEL_BG'],
        BACKGROUND_FLOATING:['BACKGROUND_SURFACE_HIGHEST'],
        TEXT_NORMAL:['TEXT_DEFAULT','TEXT_STRONG'],TEXT_MUTED:['TEXT_TERTIARY']}
    for(const map of [dark,light,midnight])for(const [old,keys] of Object.entries(aliases))
        if(map[old])for(const key of keys)map[key]??=map[old]!
    const background=data.background
    return {name:data.name.slice(0,100),description:(String(data.description??'')+' · '+(Array.isArray(data.authors)?data.authors.map((a:any)=>String(a?.name??'')).join(', '):'')).slice(0,180),
        colors:{semantic:{dark,darker:dark,light,midnight},raw},
        ...(typeof background?.url==='string'&&/^https:\/\//i.test(background.url)?{background:{
            path:background.url.slice(0,1200),opacity:typeof background.alpha==='number'?Math.max(0,Math.min(1,background.alpha)):undefined,
            blur:typeof background.blur==='number'?Math.max(0,Math.min(100,background.blur)):undefined,
        }}:{})}
}
function fontFamilies(files:Record<string,string>){
    const families:Record<string,any[]>={}
    const weights:Record<string,number>={Normal:400,Medium:500,Semibold:600,SemiBold:600,Bold:700,ExtraBold:800,Black:900}
    for(const [name,path] of Object.entries(files)){
        const italic=name.endsWith('Italic'),family=italic?name.slice(0,-6):name
        const weight=weights[family.slice(family.lastIndexOf('-')+1)]??400
        ;(families[family]??=[]).push({path,weight,italic})
    }
    return families
}
function registerAll(store:any,force=false){
    if(registeredStore===store&&!force)return
    registeredStore=store
    store.registerProvider(ID,{name:'Veyra Studio'})
    store.registerGroup(ID,OWN,{name:'Veyra Studio',description:'Eigene Discord-Designs'})
    store.registerGroup(ID,GROUP,{name:'Importiert',description:'Legacy-Themes und Fonts'})
    for(const p of allPalettes){
        const theme=ownTheme(p)
        store.registerTheme(ID,OWN,p.id,{name:theme.name,description:theme.description,getColors:()=>theme.colors})
    }
    for(const [url,theme] of Object.entries(imports))store.registerTheme(ID,GROUP,url,{name:theme.name,description:theme.description,getColors:()=>theme.colors,background:theme.background})
    for(const [url,font] of Object.entries(fonts))store.registerFontPack(ID,GROUP,url,{name:font.name,description:font.description,families:fontFamilies(font.files)})
    store.registerFontPack(ID,OWN,'system',{name:'System UI',description:'Android-Systemschrift',families:Object.fromEntries(['ggsans-Normal','ggsans-Medium','ggsans-Semibold','ggsans-Bold','ggsans-ExtraBold'].map(k=>[k,'system-ui']))})
    store.registerEmojiPack(ID,OWN,'system',{name:'Android System Emoji',description:'Emoji-Schrift des Geräts',font:'system'})
}
const tokens=previewTokens
function previewOf(store:any,plugin:string,group:string,id:string,item:any){
    const own=plugin===ID&&group===OWN?allPalettes.find(p=>p.id===id):undefined
    const fallback=own?previewPalette(own):undefined
    try{
        const colors=store.previewColorsOf(plugin,group,id,'dark',tokens)
        const parsed=Array.isArray(colors)?colors.map(normalizedColor):[]
        if(parsed.some(Boolean))return tokens.map((_,i)=>parsed[i]??fallback?.[i]??null)
    }catch{}
    if(fallback)return fallback
    if(plugin===ID&&imports[id]){
        const c=imports[id]!.colors.semantic.dark
        return tokens.map(k=>normalizedColor(c[k])??null)
    }
    return []
}
function snapshot(store:any){
    if(!store)return {themeState:{ready:false,entries:[],message:engineError()}}
    const state=store.themesStore.getState(),selected=state.selected??{}
    const entries:any[]=[]
    for(const [plugin,groups] of Object.entries(state.groups??{}) as [string,any][])
        for(const [group,items] of Object.entries(groups??{}) as [string,any][])
            for(const kind of ['themes','fontPacks','iconPacks','emojiPacks'])
                for(const [id,item] of Object.entries(items[kind]??{}) as [string,any][]){
                    if(entries.length>=180)break
                    const s=kind==='themes'?selected.colors:kind==='iconPacks'?selected.icons:kind==='emojiPacks'?selected.emoji:null
                    const active=kind==='fontPacks'?Object.values(selected.fonts?.families??{}).some((f:any)=>f.plugin===plugin&&f.group===group&&f.pack===id):
                        s?.plugin===plugin&&s?.group===group&&(s.theme??s.pack)===id
                    entries.push({plugin,group,id,kind,name:String(item.name??id).slice(0,100),description:String(item.description??'').slice(0,180),
                        imported:plugin===ID&&group===GROUP,own:plugin===ID&&group===OWN,active,
                        preview:kind==='themes'?previewOf(store,plugin,group,id,item):[],
                        detail:kind==='fontPacks'?Object.keys(item.families??{}).slice(0,5).join(', '):kind==='iconPacks'?Object.keys(item.icons??{}).length+' Icons':'',
                        background:kind==='themes'&&typeof item.background?.path==='string'?item.background.path.slice(0,500):''})
                }
    const bg=selected.background??{source:'theme'}
    const active=selected.colors
    const applied=active?.values?.semantic?.dark
    const activeColors=applied?tokens.map(k=>normalizedColor(applied[k])??null):[]
    return {themeState:{ready:true,entries,background:bg,motionRendered:[...renderedMotion],
        activeTheme:active?{plugin:active.plugin,group:active.group,id:active.theme,colors:activeColors}:null,
        defaults:{themes:!selected.colors,fontPacks:!Object.keys(selected.fonts?.families??{}).length,iconPacks:!selected.icons,emojiPacks:!selected.emoji}}}
}
async function command(op:string,json:string){
    const store=themeStore()
    if(!store)return snapshot(null)
    await load();registerAll(store);patchMotion()
    const selection=store.themesStore.getState().selected?.colors
    if(selection?.plugin===ID&&selection.group===OWN&&allPalettes.some(p=>p.id===selection.theme)){
        // Upstream registerTheme refreshes selected themes asynchronously.
        // Explicit selection here also handles early startup token initialization.
        await store.selectTheme(ID,OWN,selection.theme)
    }
    const data=JSON.parse(json)
    if(!data||typeof data!=='object')throw new Error('Ungültige Theme-Daten.')
    if(op==='theme_import'){
        if(Object.keys(imports).length>=50&&!imports[data.url])throw new Error('Maximal 50 Theme-Importe.')
        imports={...imports,[data.url]:parseLegacyTheme(data.theme)}
        await storage.set({imports,fonts},true);registerAll(store,true)
    }else if(op==='theme_font_import'){
        if(Object.keys(fonts).length>=20&&!fonts[data.url])throw new Error('Maximal 20 Font-Packs.')
        fonts={...fonts,[data.url]:data.pack};await storage.set({imports,fonts},true);registerAll(store,true)
    }else if(op==='theme_apply'||op==='theme_reset'){
        const selection=op==='theme_reset'?null:data.plugin
        const fn=({themes:'selectTheme',fontPacks:'selectFontPack',iconPacks:'selectIconPack',emojiPacks:'selectEmojiPack'} as Record<string,string>)[data.kind]
        if(!fn||typeof store[fn]!=='function')throw new Error('Theme-Typ nicht unterstützt.')
        if(selection!==null&&!store.themesStore.getState().groups[selection]?.[data.group]?.[data.kind]?.[data.id])throw new Error('Pack nicht mehr verfügbar.')
        await store[fn](selection,data.group,data.id)
    }else if(op==='theme_remove'){
        const isFont=data.kind==='fontPacks',map=isFont?fonts:imports
        if(!Object.prototype.hasOwnProperty.call(map,data.url))throw new Error('Import nicht gefunden.')
        const selected=store.themesStore.getState().selected
        if(isFont&&Object.values(selected.fonts?.families??{}).some((f:any)=>f.plugin===ID&&f.pack===data.url))await store.selectFontPack(null)
        if(!isFont&&selected.colors?.plugin===ID&&selected.colors.theme===data.url)await store.selectTheme(null)
        const {[data.url]:_,...rest}=map
        if(isFont)fonts=rest as Record<string,FontData>;else imports=rest as Record<string,ThemeData>
        await storage.set({imports,fonts},true);store.unregisterAll(ID);registerAll(store,true)
    }else if(op==='theme_background'){
        if(typeof store.setBackgroundSettings!=='function')throw new Error('Background API fehlt.')
        const clamp=(n:unknown,fallback:number,max=1)=>typeof n==='number'?Math.max(0,Math.min(max,n)):fallback
        const source=data.source
        if(!['theme','custom','none'].includes(source))throw new Error('Ungültige Hintergrundquelle.')
        if(data.motionTheme===true){
            const preset=typeof data.motion==='string'?motionById.get(data.motion):undefined
            if(source!=='custom'||!preset||typeof data.path!=='string'||motionIdFromUri('file://'+data.path)!==preset.id)
                throw new Error('Ungültiges Motion-Theme.')
            if(!store.themesStore.getState().groups[ID]?.[OWN]?.themes?.[preset.palette.id])
                throw new Error('Motion-Farbpalette nicht registriert.')
            await store.selectTheme(ID,OWN,preset.palette.id)
        }
        await store.setBackgroundSettings({source,...(source==='custom'?{path:data.path}:{}),opacity:clamp(data.opacity,1),blur:clamp(data.blur,0,40),dimming:clamp(data.dimming,.7)})
    }else if(op!=='theme_snapshot')throw new Error('Unbekannte Theme-Aktion.')
    return snapshot(store)
}

/** Two independently animated image layers inside Discord's clipped
 * background container. The chat, messages, icons and media are untouched. */
function MotionImage({id,imageProps}:{id:string,imageProps:any}){
    const preset=motionById.get(id)!
    const phase=useRef(new Animated.Value(0)).current
    const ambient=useRef(new Animated.Value(0)).current
    useEffect(()=>{
        let alive=true
        let reduced=false
        let active=AppState.currentState??'active'
        let animation:ReturnType<typeof Animated.loop>|undefined
        let lightAnimation:ReturnType<typeof Animated.loop>|undefined
        const update=()=>{
            animation?.stop();lightAnimation?.stop()
            if(!alive)return
            if(reduced){phase.setValue(0);ambient.setValue(0);return}
            if(active!=='active')return
            phase.setValue(0);ambient.setValue(0)
            animation=Animated.loop(Animated.timing(phase,{
                toValue:1,duration:preset.duration*2,easing:Easing.linear,
                useNativeDriver:true,isInteraction:false
            }))
            lightAnimation=Animated.loop(Animated.timing(ambient,{
                toValue:1,duration:ambientDuration(preset),easing:Easing.linear,
                useNativeDriver:true,isInteraction:false
            }))
            animation.start();lightAnimation.start()
        }
        const subscription=AppState.addEventListener('change',state=>{active=state;update()})
        const accessibility=AccessibilityInfo?.addEventListener?.('reduceMotionChanged',value=>{reduced=value;update()})
        Promise.resolve(AccessibilityInfo?.isReduceMotionEnabled?.()).then(value=>{
            if(!alive)return
            reduced=value===true;update()
        }).catch(()=>{if(alive)update()})
        if(!renderedMotion.has(id)){
            renderedMotion.add(id)
            nativeLoggingHook('[ValancedX Motion] Dual layers rendered '+id,1)
        }
        return()=>{alive=false;animation?.stop();lightAnimation?.stop();subscription.remove();accessibility?.remove()}
    },[phase,ambient,id,preset.duration])
    const flat=StyleSheet.flatten(imageProps.style)??{}
    const width=typeof flat.width==='number'?flat.width:360
    const height=typeof flat.height==='number'?flat.height:720
    const frames=[0,.25,.5,.75,1]
    const x=preset.motion==='pulse'?[0,0,0,0,0]:
        preset.motion==='sweep'?[-.08,0,.08,0,-.08]:
        preset.motion==='orbit'?[0,.075,0,-.075,0]:[0,.065,.09,.035,0]
    const y=preset.motion==='orbit'?[-.055,0,.055,0,-.055]:
        preset.motion==='drift'?[0,-.04,0,.04,0]:[0,0,0,0,0]
    const zoom=preset.motion==='pulse'?[1.04,1.13,1.23,1.13,1.04]:[1.27,1.29,1.30,1.29,1.27]
    const base=createElement(Animated.Image,{
        ...imageProps,
        key:'base',
        style:[imageProps.style,{transform:[
            {translateX:phase.interpolate({inputRange:frames,outputRange:x.map(v=>v*width)})},
            {translateY:phase.interpolate({inputRange:frames,outputRange:y.map(v=>v*height)})},
            {scale:phase.interpolate({inputRange:frames,outputRange:zoom})},
        ]}]
    })
    const originalUri=imageProps.source?.uri
    const lightUri=typeof originalUri==='string'?ambientSource(originalUri):originalUri
    const legacy=lightUri===originalUri
    // Older backgrounds use the same gradient at reduced opacity until
    // reapplied. New backgrounds have a genuinely transparent light texture.
    const light=createElement(Animated.Image,{
        key:'ambient',source:{uri:lightUri},resizeMode:imageProps.resizeMode??'cover',
        blurRadius:Math.max(legacy?10:0,Number(imageProps.blurRadius)||0),
        pointerEvents:'none',accessible:false,
        style:[imageProps.style,{
            opacity:ambient.interpolate({inputRange:frames,
                outputRange:[0,.65,1,.65,0].map(v=>ambientOpacity(flat.opacity,v)*(legacy ? .65 : 1))}),
            transform:[
                {translateX:ambient.interpolate({inputRange:frames,outputRange:[.04,-.13,-.04,.13,.04].map(v=>v*width)})},
                {translateY:ambient.interpolate({inputRange:frames,outputRange:[-.08,-.015,.08,.015,-.08].map(v=>v*height)})},
                {scale:ambient.interpolate({inputRange:frames,outputRange:[1.30,1.42,1.52,1.42,1.30]})},
            ],
        }]
    })
    return createElement(Fragment,null,base,light)
}
function patchMotion(){
    if(motionCleanup)return
    const cleanups:Array<()=>void>=[]
    cleanups.push(getModules(withProps<any>('CustomThemedGradient','validateColors'),module=>{
        if(typeof module.default!=='function')return
        cleanups.push(after(module,'default',(result:any)=>{
            if(!isValidElement(result))return result
            const props:any=result.props
            let changed=false
            const children=Children.map(props?.children,(child:any)=>{
                if(!isValidElement(child))return child
                const imageProps:any=child.props
                const id=motionIdFromUri(imageProps?.source?.uri)
                if(!id)return child
                changed=true
                return createElement(MotionImage,{key:'veyra-motion-'+id,id,imageProps})
            })
            return changed?cloneElement(result as any,undefined,children):result
        }))
    },{cached:false}))
    motionCleanup=()=>{for(const cleanup of cleanups.reverse())try{cleanup()}catch{};motionCleanup=undefined}
}

// Dedicated request IDs: async plugin lifecycle replies cannot consume a theme response.
const native=callNativeMethodSync as any
async function poll(){
    if(polling)return
    polling=true
    try{
        const raw=native('valancedx.manager.themeClaim',[])
        if(!raw)return
        const request=JSON.parse(raw)
        let result:any
        try{result=await command(request.operation,request.data)}
        catch(e){result={error:e instanceof Error?e.message:String(e)}}
        native('valancedx.manager.themeFinish',[request.id,JSON.stringify(result)])
    }catch(e){nativeLoggingHook('[ValancedX Theme] '+String(e),2)}finally{polling=false}
}
registerManagerThemeWake(()=>{void poll()})
try {native('valancedx.manager.themesReady',[])} catch(e) {
    nativeLoggingHook('[ValancedX Theme] Cannot announce dispatcher: '+String(e),2)
}
registerInternalPlugin(manifest,{
    async start({cleanup}){
        const store=themeStore()
        if(store){await load();registerAll(store);patchMotion()}
        cleanup(()=>{motionCleanup?.()})
    },
})
