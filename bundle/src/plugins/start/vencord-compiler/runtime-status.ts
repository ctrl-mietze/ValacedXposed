import { callNativeMethodSync } from '@valenced/modules/native'

export function publishCompilerState(id:string,expected:string[],bound:string[],state='running'){
    try{
        (callNativeMethodSync as any)('valancedx.compiler.state',[id,JSON.stringify({expected,bound,state})])
    }catch{}
}
