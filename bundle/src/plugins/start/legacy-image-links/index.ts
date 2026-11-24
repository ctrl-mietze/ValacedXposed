import { registerInternalPlugin } from '@valenced/plugins/_'
import { getModules } from '@valenced/modules/finders'
import { withName } from '@valenced/modules/finders/filters'
import { after } from '@valenced/patcher'
import { imageLinks } from '../legacy-compat/transforms'
import manifest from './manifest.json'
registerInternalPlugin(manifest,{
    start({cleanup,plugin}){
        cleanup(getModules(withName<any>('RowManager'),module=>{
            const prototype=module?.prototype
            if(typeof prototype?.generate!=='function'){plugin.reportError('RowManager.generate not available');return}
            cleanup(after(prototype,'generate',(row:any)=>{
                try{return imageLinks(row)}catch{return row}
            }))
        },{max:1,cached:false}))
    },
})
