import { registerInternalPlugin, pList, isPluginStopped } from '@valenced/plugins/_'
import manifest from './manifest.json'
registerInternalPlugin(manifest,{
    start(){},
    async stop(){
        // Earlier imported bundles might not declare this dependency yet.
        for(const p of pList.values())if(p.manifest.id.startsWith('veyra.legacy.')&&p.manifest.id!==manifest.id&&!isPluginStopped(p)){
            try{await p.stop()}catch(e){p.reportError(e)}
        }
    },
})
