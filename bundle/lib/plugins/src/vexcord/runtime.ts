import { callNativeMethodSync } from '@valenced/modules/native'
import { getJsonStorage, pluginStoragePathFor } from '@valenced/json-storage'
import type { PluginApi } from '../types'
import { createVexcordScope } from './scope'
import type { RuntimeInfo, VexcordRoot, VexcordScope, VexcordDefinition } from './contracts'

declare module '@valenced/modules/native' {
    export interface NativeMethods {
        'vexcord.runtime.info': [[], RuntimeInfo]
    }
}
export const vexcordRoot:VexcordRoot=Object.freeze({
    name:'Vexcord' as const,apiVersion:1 as const,
    capabilities:Object.freeze(['modules','hooks','events','storage','messages','lifecycle']),
    runtimeInfo(){return Object.freeze(callNativeMethodSync('vexcord.runtime.info',[]))},
    definePlugin<T extends VexcordDefinition>(options:T):T{
        if(!options||typeof options!=='object'||Array.isArray(options))throw new Error('Vexcord plugin options must be an object')
        for(const k of ['preInit','init','start','stop','SettingsComponent'])
            if((options as any)[k]!==undefined&&typeof (options as any)[k]!=='function')throw new Error('Invalid plugin lifecycle '+k)
        return options
    },
})
/** Install only at start. Existing preInit/init and unscoped APIs are untouched. */
export function attachVexcordScope(api:PluginApi):VexcordScope{
    const id=api.plugin.manifest.id
    return createVexcordScope(id,{
        observe(properties,cb){
            const finders=api.unscoped.modules.finders
            return finders.getModules(finders.filters.withProps<any>(properties[0]!,...properties.slice(1)),cb,{cached:false,max:16})
        },
        before(target,key,fn){return (api.unscoped.patcher.before as any)(target,key,fn)},
        after(target,key,fn){return (api.unscoped.patcher.after as any)(target,key,fn)},
        event(name,observer){return api.unscoped.discord.flux.onFluxEventDispatched(name as any,payload=>{observer(payload);return payload})},
        storage<T extends Record<string,any>>(file:string,defaults:T){
            const state=getJsonStorage<T>(pluginStoragePathFor(id,file),{default:defaults})
            let writes:Promise<unknown>=Promise.resolve()
            let loaded:Promise<T>|undefined
            const get=()=>loaded??=(state.get() as Promise<T>)
            const write=(v:Partial<T>|T,replace:boolean)=>{
                const result=writes.then(async()=>{await get();if(replace)await state.set(v as T,true);else await state.set(v as any)})
                writes=result.catch(()=>{})
                return result
            }
            return {
                async get(){await writes;await get();return {...(state.cache??defaults)} as T},
                set(v:Partial<T>){return write(v,false)},
                replace(v:T){return write(v,true)},
                subscribe(listener:(value:T)=>void){return state.subscribe(()=>listener({...state.cache} as T))},
            }
        },
        cleanup:fn=>api.cleanup(fn),
        error:operation=>api.plugin.reportError(new Error('Vexcord SDK: '+operation+' failed')),
    })
}
