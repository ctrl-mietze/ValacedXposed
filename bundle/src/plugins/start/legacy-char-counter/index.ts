import { registerInternalPlugin } from '@valenced/plugins/_'
import { getModules } from '@valenced/modules/finders'
import { createFilterGenerator, withProps } from '@valenced/modules/finders/filters'
import { instead } from '@valenced/patcher'
import { createElement, useState, useImperativeHandle } from 'react'
import { Text, View } from 'react-native'
import manifest from './manifest.json'
let maximum:any
function Counter({ref,style}:any){
    const [length,setLength]=useState(0)
    useImperativeHandle(ref,()=>({onMessageLengthChanged:setLength}),[])
    const max=Number(maximum?.getMaxMessageLength?.())||2000
    if(!length)return null
    return createElement(View,{style,pointerEvents:'none'},
        createElement(Text,{style:{fontSize:10,color:length>=max?'#ff8d9f':length>=max*.9?'#f7c66b':'#aeb5c6',paddingHorizontal:5}},length+'/'+max))
}
const counterFilter=createFilterGenerator(([name]:any,_,exports:any)=>exports?.type?.displayName===name,([name]:any)=>'veyra.legacy.counter:'+name)
registerInternalPlugin(manifest,{
    start({cleanup,plugin}){
        if(plugin.startedLate){plugin.requireReload();return}
        cleanup(getModules(withProps<any>('getMaxMessageLength'),m=>{maximum=m},{max:1}),
            getModules((counterFilter as any)('ChatInputCharCounter'),(module:any)=>{
                if(typeof module?.type==='function')cleanup(instead(module,'type',([props]:any[])=>createElement(Counter,props)))
            },{max:1,cached:false}))
    },
    stop({plugin}){maximum=undefined;plugin.requireReload()},
})
