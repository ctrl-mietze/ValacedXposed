/** Bounded portable text operations generated from supported AST patterns. */
export type Program = {op:'moment.rounding';rounding:'floor'|'ceil'|'round'|'trunc';desktopStop:string}
    | {op:'text.codeblockIndent';tabWidth:number;render:boolean;send:boolean;edit:boolean}

export function removeIndent(value:string,tabWidth:number):string{
    if(value.length>100_000||!Number.isInteger(tabWidth)||tabWidth<1||tabWidth>8)return value
    const text=value.replace(/\t/g,' '.repeat(tabWidth))
    const lines=text.split('\n')
    let indent=Infinity
    for(const line of lines)if(/\S/.test(line))indent=Math.min(indent,line.match(/^ */)?.[0].length??0)
    if(!Number.isFinite(indent)||indent===0)return text
    return lines.map(line=>line.replace(new RegExp('^ {0,'+indent+'}'),'')).join('\n')
}
export function normalizeCodeFences(value:string,tabWidth:number):string{
    if(value.length>100_000)return value
    return value.replace(/\x60\x60\x60([^\n\x60]*)\n([\s\S]*?)\x60\x60\x60/g,(_,header,body)=>
        '\x60\x60\x60'+header+'\n'+removeIndent(body,tabWidth)+'\x60\x60\x60')
}
/** Copy only the paths containing a code block; never modify Discord's stores. */
export function normalizeRenderedRows<T>(input:T,tabWidth:number):T{
    let budget=12000
    function visit(v:any,depth:number):any{
        if(--budget<0||depth>24||!v||typeof v!=='object')return v
        if(Array.isArray(v)){
            let changed=false
            const out=v.map(x=>{const n=visit(x,depth+1);if(n!==x)changed=true;return n})
            return changed?out:v
        }
        if(v.type==='codeBlock'&&typeof v.content==='string'){
            const content=removeIndent(v.content,tabWidth)
            return content===v.content?v:{...v,content}
        }
        let out:any=v
        for(const key of Object.keys(v)){
            if(key==='__proto__'||key==='constructor'||key==='prototype')continue
            const x=v[key],n=visit(x,depth+1)
            if(n!==x){if(out===v)out={...v};out[key]=n}
        }
        return out
    }
    return visit(input,0)
}
