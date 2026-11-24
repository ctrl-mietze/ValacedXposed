import type {Dispose, VexcordScope, VexcordStorage} from './contracts'

export interface VexcordServices {
    observe(properties: readonly string[], callback: (module: Record<string, any>) => void): Dispose
    before(target: object, key: string, transform: (args: any[]) => any[]): Dispose
    after(target: object, key: string, transform: (result: any) => any): Dispose
    event(name: string, observer: (payload: any) => void): Dispose
    storage<T extends Record<string, any>>(file: string, defaults: T): VexcordStorage<T>
    cleanup(fn: Dispose): void
    error(operation: string): void
}

export function storageFile(name: string): string {
    if(!/^[A-Za-z][A-Za-z0-9_-]{0,47}$/.test(name))throw new Error('Invalid Vexcord storage name')
    return 'vexcord-'+name+'.json'
}
export function transformMessageArgs(args: any[], index: number, transform: (text:string,channelId:string)=>string): any[] {
    const value=args[index], channel=args[0]
    if(typeof channel!=='string')return args
    const descriptor=value&&typeof value==='object'&&!Array.isArray(value)?Object.getOwnPropertyDescriptor(value,'content'):undefined
    const text=typeof value==='string'?value:descriptor?.value
    if(typeof text!=='string'||text.length>100000)return args
    if(typeof value==='object'&&Object.getPrototypeOf(value)!==Object.prototype&&Object.getPrototypeOf(value)!==null)return args
    const result=transform(text,channel)
    if(typeof result!=='string'||result.length>100000||result===text)return args
    const out=args.slice()
    out[index]=typeof value==='string'?result:{...value,content:result}
    return out
}
export function createVexcordScope(id:string,services:VexcordServices):VexcordScope {
    let alive=true
    const disposers=new Set<Dispose>()
    services.cleanup(()=>{
        alive=false
        for(const fn of [...disposers].reverse())fn()
        disposers.clear()
    })
    function requireLive(){if(!alive)throw new Error('Vexcord plugin scope is closed')}
    function track(fn:Dispose):Dispose{
        let active=true
        const off=()=>{if(!active)return;active=false;disposers.delete(off);try{fn()}catch{services.error('cleanup')}}
        if(!alive){off();return off}
        disposers.add(off);return off
    }
    function guarded<T>(name:string,fallback:T,fn:()=>T):T{
        if(!alive)return fallback
        try{return fn()}catch{services.error(name);return fallback}
    }
    function observe<T extends Record<string,any>>(properties:readonly string[],callback:(module:T)=>void):Dispose{
        requireLive()
        if(!properties.length||properties.length>12||properties.some(k=>typeof k!=='string'||!k||k.length>120))throw new Error('Invalid module selector')
        let active=true
        const seen=new WeakSet<object>()
        const off=services.observe([...new Set(properties)],m=>{
            if(!active||!alive||!m||(typeof m!=='object'&&typeof m!=='function')||seen.has(m))return
            seen.add(m);guarded('modules.observe',undefined,()=>{callback(m as T);return undefined})
        })
        return track(()=>{active=false;off()})
    }
    function before(target:object,method:string,fn:(args:any[])=>any[]|void):Dispose{
        requireLive()
        if(typeof (target as any)?.[method]!=='function')throw new Error('Missing hook target')
        return track(services.before(target,method,args=>guarded('hooks.before',args,()=>{
            const result=fn(args.slice());return Array.isArray(result)?result:args
        })))
    }
    function after(target:object,method:string,fn:(result:any)=>any):Dispose{
        requireLive()
        if(typeof (target as any)?.[method]!=='function')throw new Error('Missing hook target')
        return track(services.after(target,method,result=>guarded('hooks.after',result,()=>fn(result))))
    }
    function transform(key:'sendMessage'|'editMessage',index:number,fn:(content:string,channelId:string)=>string):Dispose{
        const offs:Dispose[]=[]
        const stop=observe<Record<string,any>>(['sendMessage','receiveMessage'],module=>{
            if(typeof module.sendMessage!=='function'||typeof module.receiveMessage!=='function'||typeof module[key]!=='function')return
            offs.push(before(module,key,args=>transformMessageArgs(args,index,fn)))
        })
        return track(()=>{stop();for(const off of offs.reverse())off()})
    }
    return Object.freeze({
        id,apiVersion:1 as const,onCleanup(fn:Dispose){requireLive();return track(fn)},
        modules:Object.freeze({observe}),
        hooks:Object.freeze({before,after}),
        events:Object.freeze({observe<T extends object>(event:string,observer:(payload:T)=>void){
            requireLive();if(!/^[A-Z][A-Z0-9_]{0,100}$/.test(event))throw new Error('Invalid Flux event')
            return track(services.event(event,p=>guarded('events.observe',undefined,()=>{observer(p as T);return undefined})))
        }}),
        storage:Object.freeze({open<T extends Record<string,any>>(name:string,defaults:T){
            requireLive();const store=services.storage(storageFile(name),defaults)
            return Object.freeze({
                get(){requireLive();return store.get()},
                set(value:Partial<T>){requireLive();return store.set(value)},
                replace(value:T){requireLive();return store.replace(value)},
                subscribe(listener:(value:T)=>void){requireLive();return track(store.subscribe(v=>guarded('storage.subscribe',undefined,()=>{listener(v);return undefined})))},
            })
        }}),
        messages:Object.freeze({
            transformOutgoing(fn:(content:string,channelId:string)=>string){return transform('sendMessage',1,fn)},
            transformEdit(fn:(content:string,channelId:string)=>string){return transform('editMessage',2,fn)},
        }),
    })
}
