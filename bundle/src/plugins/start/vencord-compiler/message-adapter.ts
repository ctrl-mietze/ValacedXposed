import { getModules } from '@valenced/modules/finders'
import { withProps } from '@valenced/modules/finders/filters'
import { before } from '@valenced/patcher'
import { changeMessageText } from './message-payload'

export type MessageTarget=Record<string,any>

/** The native client's public action module exposes send/receive/edit. Match
 * its structural ABI, not a minified desktop name. Never initialize modules. */
export function observeMessageActions(onTarget:(target:MessageTarget)=>void):()=>void{
    let live=true
    const seen=new WeakSet<object>()
    const off=getModules(withProps<any>('sendMessage','receiveMessage'),candidate=>{
        if(!live)return
        for(const target of [candidate,(candidate as any)?.default]){
            if(!target||typeof target!=='object'&&typeof target!=='function'||seen.has(target))continue
            if(!['sendMessage','receiveMessage'].every(k=>typeof target[k]==='function'))continue
            seen.add(target);onTarget(target)
        }
    },{cached:false,returnNamespace:false,max:8})
    return()=>{live=false;off()}
}

export function bindMessageText(target:MessageTarget,key:'sendMessage'|'editMessage',change:(args:any[],text:string)=>string):()=>void{
    const index=key==='sendMessage'?1:2
    if(typeof target[key]!=='function')throw new Error('Missing message action '+key)
    return before(target,key,(args:any[])=>{
        try{return changeMessageText(args,index,text=>change(args,text))}catch{return args}
    })
}
