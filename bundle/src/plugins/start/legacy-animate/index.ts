import { registerInternalPlugin } from '@valenced/plugins/_'
import { getModules } from '@valenced/modules/finders'
import { withName, withProps } from '@valenced/modules/finders/filters'
import { after } from '@valenced/patcher'
import { animatedAvatar, animateRow } from '../legacy-compat/transforms'
import manifest from './manifest.json'
registerInternalPlugin(manifest,{
    start({cleanup,plugin}){
        cleanup(getModules(withName<any>('RowManager'),module=>{
            if(typeof module?.prototype?.generate==='function')
                cleanup(after(module.prototype,'generate',(row:any)=>{try{return animateRow(row)}catch{return row}}))
        },{max:1,cached:false}),
        getModules(withProps<any>('getUserAvatarURL','getAvatarDecorationURL'),module=>{
            for(const key of ['getUserAvatarURL','getGuildMemberAvatarURLSimple','getGuildIconURL']){
                if(typeof module[key]==='function')cleanup(after(module,key,(value:any)=>animatedAvatar(value)))
            }
        },{max:1,cached:false}))
    },
})
