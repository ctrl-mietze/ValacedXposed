import { registerJSMethod, callNativeMethodSync } from '@valenced/modules/native'
import { JsonStorage, pluginStoragePathFor } from '@valenced/json-storage'
import { pList, registerInternalPlugin } from '@valenced/plugins/_'
import manifest from './manifest.json'

type ThemeData = { name: string; description: string; colors: any; background?: any }
type Saved = { imports: Record<string, ThemeData> }
const ID = 'veyra.manager.themes'
const GROUP = 'imports'
const hex = /^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i
let storage: any
let imports: Record<string, ThemeData> = {}
let loaded = false

function themeStore(): any {
    return (pList.get('valenced.settings.themes')?.api as any)?.themesInternal?.store
}

async function load() {
    if (loaded) return
    storage = new (JsonStorage as any)(pluginStoragePathFor(ID,'imports.json'),{default:{imports:{}}})
    const saved: Saved = await storage.get() ?? {imports:{}}
    imports = saved.imports ?? {}
    loaded=true
}

function normalizedColor(value: unknown): string | undefined {
    if(typeof value !== 'string' || !hex.test(value)) return
    return value.length===4?'#'+[...value.slice(1)].map(x=>x+x).join(''):value
}
function parseLegacyTheme(data:any): ThemeData {
    if(!data || typeof data!=='object' || data.spec!==2 || typeof data.name!=='string')
        throw new Error('Nur Legacy-Themes im Spec-2-Format werden unterstützt.')
    const dark: Record<string,string> = {},light:Record<string,string>={},midnight:Record<string,string>={},raw:Record<string,string>={}
    const validKey=(k:string)=>/^[A-Za-z0-9_-]{1,100}$/.test(k)&&!['__proto__','constructor','prototype'].includes(k)
    for(const [key,v] of Object.entries(data.semanticColors??{}).slice(0,1500)){
        if(!validKey(key)||!Array.isArray(v))continue
        const d=normalizedColor(v[0]),l=normalizedColor(v[1]),m=normalizedColor(v[2])??d
        if(d)dark[key]=d;if(l)light[key]=l;if(m)midnight[key]=m
    }
    for(const [key,v] of Object.entries(data.rawColors??{}).slice(0,1500)){
        const c=normalizedColor(v);if(validKey(key)&&c)raw[key]=c
    }
    if(!Object.keys(dark).length&&!Object.keys(light).length&&!Object.keys(raw).length)throw new Error('Keine gültigen Farben im Theme.')
    const background=data.background
    return {name:data.name.slice(0,100),description:(String(data.description??'')+' · '+(Array.isArray(data.authors)?data.authors.map((a:any)=>String(a?.name??'')).join(', '):'')).slice(0,180),
        colors:{semantic:{dark,darker:dark,light,midnight},raw},
        ...(typeof background?.url==='string'&&/^https:\/\//i.test(background.url)?{background:{
            path:background.url.slice(0,1200),
            opacity:typeof background.alpha==='number'?Math.max(0,Math.min(1,background.alpha)):undefined,
            blur:typeof background.blur==='number'?Math.max(0,Math.min(100,background.blur)):undefined,
        }}:{})}
}

function registerImports(store:any) {
    store.registerProvider(ID,{name:'ValancedX Imports'})
    store.registerGroup(ID,GROUP,{name:'ValancedX Imports',description:'Legacy Discord color themes'})
    for(const [url,theme] of Object.entries(imports))
        store.registerTheme(ID,GROUP,url,{name:theme.name,description:theme.description,
            getColors:()=>theme.colors,background:theme.background})
}
function snapshot(store:any) {
    if(!store)return {themeState:{ready:false,entries:[],message:'Discord Themes API installieren oder Discord neu starten.'}}
    const state=store.themesStore.getState()
    const entries:any[]=[]
    for(const [plugin,groups] of Object.entries(state.groups??{}) as [string,any][]){
        for(const [group,items] of Object.entries(groups) as [string,any][]){
            for(const kind of ['themes','fontPacks','iconPacks','emojiPacks']){
                for(const [id,item] of Object.entries(items[kind]??{}) as [string,any][]){
                    if(entries.length>=180)break
                    const selected=kind==='themes'?state.selected.colors:kind==='iconPacks'?state.selected.icons:kind==='emojiPacks'?state.selected.emoji:null
                    const active=kind==='fontPacks'?Object.values(state.selected.fonts?.families??{}).some((f:any)=>f.plugin===plugin&&f.group===group&&f.pack===id):
                        selected?.plugin===plugin&&selected?.group===group&&(selected.theme??selected.pack)===id
                    entries.push({plugin,group,id,kind,name:String(item.name??id).slice(0,100),description:String(item.description??'').slice(0,180),
                        imported:plugin===ID,active})
                }
            }
        }
    }
    return {themeState:{ready:true,entries}}
}

async function command(op:string,json:string) {
    const store=themeStore()
    if(!store)return snapshot(null)
    await load()
    const data=JSON.parse(json)
    if(!data || typeof data!=='object')throw new Error('Ungültige Theme-Daten.')
    if(op==='theme_import'){
        if(Object.keys(imports).length>=50&&!imports[data.url])throw new Error('Maximal 50 importierte Themes.')
        const theme=parseLegacyTheme(data.theme)
        imports={...imports,[data.url]:theme}
        await storage.set({imports},true)
        registerImports(store)
        // Import and selection are separate; the user can explicitly apply a theme.
    } else if(op==='theme_apply'||op==='theme_reset'){
        const selection=op==='theme_reset'?null:data.plugin
        const funcs:Record<string,string>={themes:'selectTheme',fontPacks:'selectFontPack',iconPacks:'selectIconPack',emojiPacks:'selectEmojiPack'}
        const fn=funcs[data.kind]
        if(!fn||typeof store[fn]!=='function')throw new Error('Theme-Typ nicht unterstützt.')
        if(selection!==null){
            const entry=store.themesStore.getState().groups[selection]?.[data.group]?.[data.kind]?.[data.id]
            if(!entry)throw new Error('Theme oder Pack nicht mehr verfügbar.')
        }
        await store[fn](selection,data.group,data.id)
    } else if(op==='theme_remove'){
        if(!Object.prototype.hasOwnProperty.call(imports,data.url))throw new Error('Import nicht gefunden.')
        const selection=store.themesStore.getState().selected.colors
        if(selection?.plugin===ID&&selection.theme===data.url)await store.selectTheme(null)
        const { [data.url]: _,...remaining }=imports
        imports=remaining
        await storage.set({imports},true)
        store.unregisterAll(ID)
        registerImports(store)
    } else if(op!=='theme_snapshot')throw new Error('Unbekannte Theme-Aktion.')
    return snapshot(store)
}

// Registered on JS startup even when the upstream theme stubs have not updated yet.
registerJSMethod('valancedx.manager.themes',async (operation:string,json:string)=>{
    try { return JSON.stringify(await command(operation,json)) }
    catch(e){return JSON.stringify({error:e instanceof Error?e.message:String(e)})}
})
try { (callNativeMethodSync as any)('valancedx.manager.themesReady',[]) } catch {}

registerInternalPlugin(manifest,{
    async start(){
        const store=themeStore()
        if(!store)return
        await load()
        registerImports(store)
    },
})
