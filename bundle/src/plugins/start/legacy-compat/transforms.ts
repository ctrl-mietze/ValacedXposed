/** Independent ValancedX reimplementations of classic local UI features. */
export function imageLinks(row:any){
    const message=row?.message
    if(row?.type!==1||!message||!Array.isArray(message.content)||message.content.length!==0||!Array.isArray(message.embeds))return row
    const urls=[...new Set<string>(message.embeds.filter((e:any)=>e&&(e.type==='image'||e.type==='gifv')&&typeof e.url==='string'&&/^https?:\/\//i.test(e.url)).map((e:any)=>e.url))]
    if(!urls.length)return row
    const content=urls.flatMap((url,i)=>[
        ...(i?[{type:'text',content:'\n'}]:[]),
        {type:'link',target:url,content:[{type:'text',content:url}]},
    ])
    return {...row,message:{...message,content}}
}

export function pastelColor(id:string):number{
    let hash=2166136261
    for(let i=0;i<id.length;i++)hash=Math.imul(hash^id.charCodeAt(i),16777619)
    const hue=(hash>>>0)%360/60
    const c=.48,x=c*(1-Math.abs(hue%2-1)),m=.42
    const components=hue<1?[c,x,0]:hue<2?[x,c,0]:hue<3?[0,c,x]:hue<4?[0,x,c]:hue<5?[x,0,c]:[c,0,x]
    return components.reduce((value,component)=>value*256+Math.round((component+m)*255),0)
}
export function pastelRow(row:any){
    const message=row?.message
    if(row?.type!==1||!message||typeof message.authorId!=='string'||!message.username||message.roleColor||message.usernameColor||message.colorString||message.webhookId)return row
    const color=pastelColor(message.authorId)
    // Use a deterministic RGB value; preserve any existing role color.
    const hex='#'+color.toString(16).padStart(6,'0')
    return {...row,message:{...message,roleColor:color,usernameColor:color,colorString:hex}}
}
export function animatedAvatar(uri:unknown){
    if(typeof uri!=='string')return uri
    // Only Discord's already-animated avatar/icon asset names, never uploads.
    if(!/^https:\/\/(?:cdn\.discordapp\.com|media\.discordapp\.net)\/(?:avatars|icons|guilds)\//i.test(uri))return uri
    return uri.replace(/(\/a_[a-f0-9]+)\.(?:webp|png|jpe?g)(?=\?|$)/i,'$1.gif')
}
export function animateRow(row:any){
    if(row?.type!==1||!row.message)return row
    const url=animatedAvatar(row.message.avatarURL)
    return url===row.message.avatarURL?row:{...row,message:{...row.message,avatarURL:url}}
}

const track=/^(?:utm_[a-z0-9_]+|fbclid|gclid|dclid|msclkid|twclid|yclid|igshid|mc_cid|mc_eid|_ga|_gl)$/i
const sensitive=/^(?:sig|signature|token|auth|key|expires|ex|is|hm|policy|credential|x-amz-.+|x-goog-.+)$/i
export function cleanUrl(url:string):string{
    // Keep original encoding and parameter order; never rewrite signed URLs.
    const hashAt=url.indexOf('#')
    const tail=hashAt<0?'':url.slice(hashAt)
    const base=hashAt<0?url:url.slice(0,hashAt)
    const q=base.indexOf('?')
    if(q<0)return url
    const fields=base.slice(q+1).split('&')
    const names=fields.map(field=>{try{return decodeURIComponent(field.split('=')[0]??'')}catch{return ''}})
    if(names.some(n=>!n||sensitive.test(n)))return url
    const kept=fields.filter((_,i)=>!track.test(names[i]!))
    if(kept.length===fields.length)return url
    return base.slice(0,q)+(kept.length?'?'+kept.join('&'):'')+tail
}
export function cleanText(value:unknown):unknown{
    return typeof value==='string'?value.replace(/https?:\/\/[^\s<>"\x60]+/gi,url=>{
        const tail=url.match(/[),.!?;]+$/)?.[0]??''
        return cleanUrl(tail?url.slice(0,-tail.length):url)+tail
    }):value
}
