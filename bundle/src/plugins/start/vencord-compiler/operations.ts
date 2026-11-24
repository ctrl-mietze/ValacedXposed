/** Bounded portable text operations generated from supported AST patterns. */
export type Program = {op:'moment.rounding';rounding:'floor'|'ceil'|'round'|'trunc';desktopStop:string}
    | {op:'text.codeblockIndent';tabWidth:number;render:boolean;send:boolean;edit:boolean}
    | MessageProgram

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


export type ReplacementRule={find:string;replace:string;onlyIfIncludes?:string;regex?:boolean}
export type MessageProgram =
    | {op:'message.replace';skipChannel:string;maxRules:number}
    | {op:'message.timestamp';enabledDefault:boolean}
    | {op:'message.silent';prefix:string;autoDisableDefault:boolean;persistDefault:boolean}

export function boundedRegex(pattern:string):RegExp|undefined{
    // No groups, quantifiers, lookarounds, backrefs or alternation. This
    // intentionally supports only fixed-width patterns with bounded work.
    if(!pattern||pattern.length>96)return
    let source=pattern,flags='g'
    const match=pattern.match(/^\/(.*)\/([a-z]*)$/)
    if(match){source=match[1]!;flags=match[2]||'g'}
    if(!/^[gimu]*$/.test(flags)||new Set(flags).size!==flags.length||!source)return
    let inClass=false
    for(let i=0;i<source.length;i++){
        const c=source[i]!
        if(c==='\\'){
            const next=source[++i]
            if(!next||/[0-9kpu]/i.test(next))return
            continue
        }
        if(c==='['){if(inClass)return;inClass=true;continue}
        if(c===']'){if(!inClass)return;inClass=false;continue}
        if(!inClass&&/[()*+?{}|]/.test(c))return
    }
    if(inClass)return
    try{return new RegExp(source,flags)}catch{return undefined}
}
export function applyReplacementRules(text:string,rules:ReplacementRule[],channelId:string,skipChannel:string,maxRules=24):string{
    if(channelId===skipChannel||!text||text.length>16000||!Array.isArray(rules))return text
    let current=text
    for(const rule of rules.slice(0,Math.min(24,maxRules))){
        if(!rule||typeof rule.find!=='string'||!rule.find||rule.find.length>96||typeof rule.replace!=='string'||rule.replace.length>256)continue
        if(rule.onlyIfIncludes&&(!current.includes(rule.onlyIfIncludes)||rule.onlyIfIncludes.length>128))continue
        const replacement=rule.replace.replaceAll('\\n','\n')
        let next=current
        if(rule.regex){
            const re=boundedRegex(rule.find)
            if(!re)continue
            next=current.replace(re,()=>replacement)
        }else{
            const padded=' '+current+' '
            const count=padded.split(rule.find).length-1
            if(padded.length+count*Math.max(0,replacement.length-rule.find.length)>16000)continue
            next=padded.split(rule.find).join(replacement).replace(/^\s|\s$/g,'')
        }
        if(next.length<=16000)current=next
    }
    return current===text?text:current.trim()
}
export function timestampTag(value:string,now:Date=new Date(),format='t'):string|undefined{
    const m=value.trim().match(/^(\d{1,2}):(\d{2})\s*(AM|PM)?$/i)
    if(!m)return
    let h=Number(m[1]),min=Number(m[2])
    if(min>59)return
    if(m[3]){if(h<1||h>12)return;h=h%12+(m[3].toUpperCase()==='PM'?12:0)}
    else if(h>24||h===24&&min!==0)return
    const date=new Date(now.getTime())
    date.setHours(h,min,0,0)
    if(date.getTime()<now.getTime())date.setDate(date.getDate()+1)
    if(!Number.isFinite(date.getTime()))return
    const f=/^[tTdDfFR]$/.test(format)?format:'t'
    return '<t:'+Math.round(date.getTime()/1000)+':'+f+'>'
}
export function convertTimeShortcuts(text:string,now:Date=new Date()):string{
    if(text.length>16000)return text
    // Avoid changing snippets in fenced code. A single backtick time
    // is the intentional Vencord shortcut.
    return text.split(/(\x60\x60\x60[\s\S]*?\x60\x60\x60)/g).map((part,i)=>i%2?part:
        part.replace(/\x60(\d{1,2}:\d{2} ?(?:AM|PM)?)\x60/gi,(all,time)=>timestampTag(time,now)??all)).join('')
}
export function silentPrefix(text:string,prefix:string):string{
    if(!text.trim()||text.startsWith(prefix))return text
    return prefix+text
}
