/** Both native message records and older text-only wrappers exist. Preserve
 * all non-content fields and the caller's original argument objects. */
export function messageText(value:unknown):string|undefined{
    if(typeof value==='string')return value
    if(!value||typeof value!=='object'||Array.isArray(value))return
    const proto=Object.getPrototypeOf(value)
    if(proto!==Object.prototype&&proto!==null)return
    const descriptor=Object.getOwnPropertyDescriptor(value,'content')
    return typeof descriptor?.value==='string'?descriptor.value:undefined
}
export function changeMessageText(args:any[],index:number,change:(text:string)=>string):any[]{
    const value=args[index],text=messageText(value)
    if(text===undefined)return args
    const content=change(text)
    if(typeof content!=='string'||content===text)return args
    const next=args.slice()
    next[index]=typeof value==='string'?content:{...value,content}
    return next
}
