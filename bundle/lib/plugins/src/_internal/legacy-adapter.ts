import { JsonStorage, pluginStoragePathFor } from '@valenced/json-storage'
import type { PluginOptionsFactory } from '../types'

/** Limited Revenge/Vendetta JS compatibility. A plugin runs with Discord's
 * own privileges, just like a native Valenced JS plugin. Never auto-enable. */
export function createLegacyOptionsFactory(id: string, source: string): PluginOptionsFactory {
    return () => {
        let instance: any
        let reactInstance: any
        let storage: any
        let hooks: Array<() => void> = []
        let saveTimer: ReturnType<typeof setTimeout> | undefined
        let pendingSave: (() => void) | undefined
        const watchers = new Set<() => void>()
        const clean = () => {
            for (const off of hooks.splice(0).reverse()) {
                try { off() } catch {}
            }
            if (saveTimer !== undefined) clearTimeout(saveTimer)
            saveTimer = undefined
            pendingSave?.()
            pendingSave = undefined
            watchers.clear()
        }
        return {
            async start(api) {
                const u: any = api.unscoped
                const store: any = new (JsonStorage as any)(pluginStoragePathFor(id, 'revenge.json'), { default: {} })
                const data = await store.get() ?? {}
                const notify = () => {
                    for (const watcher of watchers) watcher()
                    if (saveTimer !== undefined) clearTimeout(saveTimer)
                    pendingSave = () => {
                        pendingSave = undefined
                        const serialized = JSON.stringify(data)
                        if (serialized.length <= 262144) void store.set(JSON.parse(serialized), true)
                    }
                    saveTimer = setTimeout(() => pendingSave?.(), 180)
                }
                const proxies = new WeakMap<object, object>()
                const wrap = (value: any): any => {
                    if (value === null || typeof value !== 'object') return value
                    if (proxies.has(value)) return proxies.get(value)
                    const p = new Proxy(value, {
                        get: (target, key, receiver) => wrap(Reflect.get(target, key, receiver)),
                        set(target, key, value, receiver) {
                            if (key === '__proto__' || key === 'constructor' || key === 'prototype') return false
                            const result = Reflect.set(target,key,value,receiver); notify(); return result
                        },
                        deleteProperty(target,key) { const result=Reflect.deleteProperty(target,key);notify();return result },
                    })
                    proxies.set(value,p)
                    return p
                }
                storage = wrap(data)
                const React = reactInstance = u.react.React
                const ReactNative = u.react.ReactNative
                const finders = u.modules.finders
                const filters = finders.filters
                const lookup = (filter: any, all = false) => {
                    if (all) return Array.from(finders.lookupModules(filter,{cached:false}), (x: any) => x[0]).filter(Boolean)
                    return finders.lookupModule(filter,{cached:false})[0]
                }
                const findByProps = (...props: string[]) => props.length ? lookup(filters.withProps(...props)) : undefined
                const findByName = (name: string) => lookup(filters.withName(name))
                const findByStoreName = (name: string) => u.discord.flux.Stores[name]
                const findByDisplayName = (name: string) => {
                    const make = filters.createFilterGenerator
                    const filter = make((_: any, _id: any, value: any) => value?.displayName === name,
                        () => 'veyra.legacy.display.' + name, filters.FilterScopes.Initialized)()
                    return lookup(filter)
                }
                const common: any = { React, ReactNative }
                const getters: Record<string, () => any> = {
                    FluxDispatcher: () => findByProps('dispatch','subscribe','unsubscribe'),
                    NavigationNative: () => u.externals.ReactNavigation.ReactNavigationNative,
                    i18n: () => findByProps('Messages','getLocale'),
                    stylesheet: () => ReactNative.StyleSheet,
                    clipboard: () => u.externals.ReactNativeClipboard.Clipboard ?? findByProps('setString','getString'),
                    url: () => ReactNative.Linking,
                }
                for (const [key,getter] of Object.entries(getters))
                    Object.defineProperty(common,key,{enumerable:true,get:getter})
                // Old patcher callbacks receive original arguments AND return value.
                // Use a scoped instead-hook so both values and the receiver survive.
                function patch(mode: string, key: string, parent: any, callback: any, once = false) {
                    if (!parent || typeof parent[key] !== 'function') throw new Error('Legacy patch target missing: ' + key)
                    let unpatch: () => void
                    const off = u.patcher.instead(parent,key,function(this: any,args: any[],original: any) {
                        if (once) unpatch()
                        if (mode === 'instead') return callback(args,original.bind(this))
                        if (mode === 'before') {
                            const changed = callback(args)
                            return Reflect.apply(original,this,Array.isArray(changed) ? changed : args)
                        }
                        const result = Reflect.apply(original,this,args)
                        const changed = callback(args,result)
                        return changed === undefined ? result : changed
                    })
                    let removed=false
                    unpatch=()=>{if(!removed){removed=true;off()}}
                    hooks.push(unpatch)
                    return unpatch
                }
                const useProxy = (value: any) => {
                    const [,rerender]=React.useState(0)
                    React.useEffect(()=>{const cb=()=>rerender((x:number)=>x+1);watchers.add(cb);return()=>watchers.delete(cb)},[])
                    return value
                }
                const apiObject: any = {
                    metro: { common, findByProps, findByPropsAll: (...p:string[]) => lookup(filters.withProps(...p),true),
                        findByName,findByStoreName,findByDisplayName,
                        findByTypeName: findByName,
                    },
                    patcher: {
                        before: (k:string,p:any,c:any,o?:boolean)=>patch('before',k,p,c,o),
                        after: (k:string,p:any,c:any,o?:boolean)=>patch('after',k,p,c,o),
                        instead: (k:string,p:any,c:any,o?:boolean)=>patch('instead',k,p,c,o),
                    },
                    storage: { storage, useProxy, wrapSync:(v:any)=>v, awaitSync: async(v:any)=>v },
                    plugin: { id, manifest: api.plugin.manifest, storage },
                    plugins: { plugins: { [id]: { id, manifest: api.plugin.manifest, enabled: true } },
                        installPlugin:()=>{throw new Error('Legacy: weitere Plugins über ValancedX installieren.')},
                        removePlugin:()=>{throw new Error('Legacy: Plugins über ValancedX entfernen.')},
                    },
                    logger: { log: (...v:any[])=>console.log('[Legacy '+id+']',...v),
                        info:(...v:any[])=>console.info('[Legacy '+id+']',...v),
                        warn:(...v:any[])=>console.warn('[Legacy '+id+']',...v),
                        error:(...v:any[])=>console.error('[Legacy '+id+']',...v) },
                    utils: {
                        findInReactTree: (tree:any,predicate:any) => {
                            const seen=new Set<any>(); const visit=(node:any):any=>{
                                if(node==null||seen.has(node))return undefined
                                if(predicate(node))return node
                                if(typeof node!=='object')return undefined
                                seen.add(node)
                                for(const child of (Array.isArray(node)?node:[node.props?.children])) {
                                    const result=visit(child);if(result!==undefined)return result
                                }
                            };return visit(tree)
                        },
                        safeFetch: async (url:string,options:any)=>{const r=await fetch(url,options);if(!r.ok)throw new Error('HTTP '+r.status);return r},
                        without: (obj:any,...keys:string[])=>Object.fromEntries(Object.entries(obj).filter(([key])=>!keys.includes(key))),
                    },
                    ui: {
                        assets:{getAssetIDByName:u.assets.getAssetIdByName,getAssetByName:u.assets.getAssetByName},
                        alerts:{
                            showConfirmationAlert:(opts:any)=>ReactNative.Alert.alert(String(opts.title??'Revenge Plugin'),
                                typeof opts.content==='string'?opts.content:typeof opts.body==='string'?opts.body:'',[
                                    {text:opts.cancelText??'Abbrechen',style:'cancel',onPress:opts.onCancel},
                                    {text:opts.confirmText??'OK',onPress:opts.onConfirm},
                                ]),
                        },
                        toasts:{showToast:(text:string)=>u.discord.actions.ToastActionCreators.open('VEYRA_LEGACY_'+Date.now(),{text})},
                        components:{
                            get Forms(){const d=u.discord.design.Design;return {FormRow:d.TableRow,FormSection:d.TableRowGroup,
                                FormText:d.Text,FormSwitch:d.TableSwitchRow,FormInput:d.TextInput}},
                            get General(){return {...ReactNative,Text:u.discord.design.Design.Text}},
                            get Search(){return u.components.SearchInput},
                        },
                    },
                }
                // Existing compiled scripts are expressions (typically an IIFE).
                // Never evaluate downloaded script during import or preInit.
                try {
                    const result = new Function('vendetta','revenge','bunny',
                        'return ('+source.trim().replace(/;+\s*$/,'')+'\n)\n//# sourceURL=ValancedX:Legacy:'+id)(apiObject,apiObject,apiObject)
                    instance=result?.default ?? result
                    if(!instance||typeof instance!=='object')throw new Error('Unsupported legacy plugin export')
                    if(typeof instance.onLoad==='function') await instance.onLoad()
                    else if(typeof instance.start==='function') await instance.start()
                    else if(!instance.settings)throw new Error('No supported lifecycle in legacy plugin')
                    api.cleanup(clean)
                } catch(e) { clean();throw e }
            },
            async stop() {
                try { if(typeof instance?.onUnload==='function')await instance.onUnload()
                    else if(typeof instance?.stop==='function')await instance.stop()
                } finally { clean();instance=undefined }
            },
            SettingsComponent: () => {
                const Component=instance?.settings
                if(typeof Component!=='function')return null
                return reactInstance?.createElement?.(Component) ?? null
            },
        }
    }
}
