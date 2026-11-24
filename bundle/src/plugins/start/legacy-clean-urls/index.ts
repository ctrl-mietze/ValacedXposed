import { registerInternalPlugin } from '@valenced/plugins/_'
import { Clipboard } from '@valenced/externals/react-native-clipboard'
import { getModules } from '@valenced/modules/finders'
import { withName } from '@valenced/modules/finders/filters'
import { instead } from '@valenced/patcher'
import { cleanText } from '../legacy-compat/transforms'
import manifest from './manifest.json'
registerInternalPlugin(manifest,{
    start({cleanup}){
        if(typeof Clipboard?.setString==='function')
            cleanup(instead(Clipboard,'setString',(args:any[],original:any)=>original(cleanText(args[0]))))
        cleanup(getModules(withName<any>('Messages'),(m:any)=>{
            const target=m?.default??m
            for(const [key,index] of [['sendMessage',1],['editMessage',2]] as const){
                if(typeof target?.[key]!=='function')continue
                cleanup(instead(target,key,(args:any[],original:any)=>{
                    if(typeof args[index]==='string')args[index]=cleanText(args[index])
                    return original(...args)
                }))
            }
        },{returnNamespace:true,max:1,cached:false}))
    },
})
