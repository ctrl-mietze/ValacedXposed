import { registerInternalPlugin } from '@valenced/plugins/_'
import { onFluxEventDispatched } from '@valenced/discord/flux'
import manifest from './manifest.json'
registerInternalPlugin(manifest,{
    start({cleanup}){cleanup(onFluxEventDispatched('TYPING_START_LOCAL',()=>undefined))}
})
