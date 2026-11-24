import { getModules } from '@valenced/modules/finders'
import { withProps } from '@valenced/modules/finders/filters'
import { before } from '@valenced/patcher'
import type { PluginOptions } from '@valenced/plugins/types'
import { createMessageOptions } from './message-runtime'
import { createContextOptions } from './context-runtime'
import { observeMessageActions, bindMessageText } from './message-adapter'
import { publishCompilerState } from './runtime-status'
import { normalizeCodeFences, normalizeRenderedRows, type Program } from './operations'

/** Compiler runtime uses named mobile API targets and reversible operations.
 * No Vencord source eval, desktop patches, arbitrary native calls or new imports. */
export function createCompiledOptions(program:Program):PluginOptions{
    if(program.op==='message.replace'||program.op==='message.timestamp'||program.op==='message.silent')return createMessageOptions(program)
    if(program.op==='menu.userUrl'||program.op==='menu.emojiMarkdown'||program.op==='reply.mentionPolicy')return createContextOptions(program)
    return {
        start({cleanup,plugin}){
            const expected=new Set<string>()
            const bound=new Set<string>()
            let alive=true
            function publish(){publishCompilerState(plugin.manifest.id,[...expected],[...bound])}
            function mark(name:string){bound.add(name);publish();nativeLoggingHook('[ValancedX Compiler] '+program.op+' bound '+name,1)}
            function fail(error:unknown){plugin.reportError(error)}
            cleanup(()=>{alive=false;publishCompilerState(plugin.manifest.id,[],[],'stopped')})
            if(program.op==='moment.rounding'){
                expected.add('Moment')
                const rounder=({floor:Math.floor,ceil:Math.ceil,round:Math.round,trunc:Math.trunc})[program.rounding]
                if(typeof rounder!=='function')throw new Error('Invalid rounding IR')
                cleanup(getModules(withProps<any>('relativeTimeRounding','relativeTimeThreshold'),module=>{
                    if(!alive||bound.has('Moment'))return
                    try{
                        const previous=module.relativeTimeRounding()
                        if(typeof previous!=='function')throw new Error('Unsupported Moment rounding API')
                        if(module.relativeTimeRounding(rounder)!==true)throw new Error('Moment rejected rounding operation')
                        cleanup(()=>{if(module.relativeTimeRounding()===rounder)module.relativeTimeRounding(previous)})
                        mark('Moment')
                    }catch(e){fail(e)}
                },{max:1,cached:false}))
            }else if(program.op==='text.codeblockIndent'){
                const tabWidth=program.tabWidth
                if(!Number.isInteger(tabWidth)||tabWidth<1||tabWidth>8)throw new Error('Invalid tab width IR')
                if(program.render){
                    expected.add('ChatRows')
                    cleanup(getModules(withProps<any>('updateRows','clearRows','scrollToBottom'),commands=>{
                        if(!alive||bound.has('ChatRows'))return
                        try{
                            cleanup(before(commands,'updateRows',(args:any[])=>{
                                try{
                                    const payload=args[1]
                                    if(!Array.isArray(payload?.rows))return args
                                    const rows=normalizeRenderedRows(payload.rows,tabWidth)
                                    return rows===payload.rows?args:[args[0],{...payload,rows},...args.slice(2)]
                                }catch{return args}
                            }))
                            mark('ChatRows')
                        }catch(e){fail(e)}
                    },{max:1,cached:false}))
                }
                if(program.send||program.edit){
                    const targets=[...(program.send?[['sendMessage',1] as const]:[]),...(program.edit?[['editMessage',2] as const]:[])]
                    for(const [key] of targets)expected.add(key)
                    cleanup(observeMessageActions(target=>{
                        if(!alive)return
                        for(const [key] of targets){
                            if(bound.has(key)||typeof target[key]!=='function')continue
                            try{
                                cleanup(bindMessageText(target,key,(_args,text)=>normalizeCodeFences(text,tabWidth)))
                                mark(key)
                            }catch(e){fail(e)}
                        }
                    }))
                }
            }else throw new Error('Unknown compiler operation')
            // A lazy Metro module may load later. Report pending targets without
            // pretending the port has already bound them or crashing the host.
            publish()
            const timer=setTimeout(()=>{
                if(!alive)return
                const missing=[...expected].filter(x=>!bound.has(x))
                publish()
                if(missing.length)nativeLoggingHook('[ValancedX Compiler] Pending '+program.op+': '+missing.join(', '),2)
            },8000)
            cleanup(()=>clearTimeout(timer))
        },
        stop({plugin}){if(program.op==='text.codeblockIndent'&&program.render)plugin.requireReload()},
    }
}
