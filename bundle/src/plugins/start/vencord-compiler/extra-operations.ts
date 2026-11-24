import {snowflake} from './context-operations'

export type ExtraProgram=
    | {op:'input.gifPaste';location:'gif_reply'}
    | {op:'menu.quickMention';label:string}
    | {op:'menu.voiceFile';label:string}
    | {op:'menu.imageFilename';label:string;fullUrlDefault:boolean}
    | {op:'menu.copyTextFile';label:string;maxBytes:number}
    | {op:'menu.reverseImage';label:string;engines:{name:string;base:string}[]}
    | {op:'menu.originalImage';label:string}
    | {op:'menu.rawSelection';label:string}
    | {op:'menu.serverInfo';label:string}
    | {op:'menu.latency';label:string;threshold:number}
    | {op:'rows.replyTimestamp'}
    | {op:'rows.gifAlt'}
    | {op:'rows.ircColors';lightness:number;saturation:number;seed:string;onlyUncolored:boolean;onlyDMs:boolean}

export type MenuProgram=Extract<ExtraProgram,{op:'menu.quickMention'|'menu.voiceFile'|'menu.imageFilename'|'menu.copyTextFile'|'menu.reverseImage'|'menu.originalImage'|'menu.rawSelection'|'menu.serverInfo'|'menu.latency'}>
export type RowProgram=Extract<ExtraProgram,{op:'rows.replyTimestamp'|'rows.gifAlt'|'rows.ircColors'}>
const epoch=1420070400000
export function snowflakeTime(id:unknown):number|undefined{
    if(!snowflake(id))return
    try{const n=Number((BigInt(id)>>22n)+BigInt(epoch));return n>=epoch&&n<=Date.now()+86400000?n:undefined}catch{return undefined}
}
export function dateForId(id:unknown):string|undefined{
    const t=snowflakeTime(id);if(t===undefined)return
    return new Date(t).toLocaleString()
}
export function latencyEstimate(id:unknown,nonce:unknown):number|undefined{
    const a=snowflakeTime(id),b=snowflakeTime(nonce)
    if(a===undefined||b===undefined)return
    const diff=a-b
    // Nonces are not trusted clocks or network RTT measurements.
    return Math.abs(diff)<=86400000?diff:undefined
}
export function discordAttachmentUrl(value:unknown):string|undefined{
    if(typeof value!=='string'||value.length>4000)return
    try{
        const u=new URL(value)
        if(u.protocol!=='https:'||u.username||u.password||!['cdn.discordapp.com','media.discordapp.net'].includes(u.hostname))return
        if(!/^\/attachments\/\d+\/\d+\/[^/]+$/.test(u.pathname))return
        return value
    }catch{return undefined}
}
export function voiceFile(message:any):string|undefined{
    const attachments=Array.isArray(message?.attachments)?message.attachments:[]
    const isVoice=typeof message?.flags==='number'&&(message.flags&8192)!==0
    const a=attachments.find((v:any)=>v&&typeof v==='object'&&(isVoice||typeof v.waveform==='string'||typeof v.duration_secs==='number')&&
        (typeof v.content_type==='string'&&v.content_type.startsWith('audio/')||/\.(ogg|opus|m4a|mp3)$/i.test(v.filename??'')))
    return discordAttachmentUrl(a?.url)
}
export function imageName(value:unknown):string|undefined{
    if(typeof value!=='string'||value.length>4000)return
    try{
        const u=new URL(value)
        if(u.protocol!=='https:'&&u.protocol!=='http:')return
        const raw=u.pathname.split('/').pop()??''
        let name=raw
        try{name=decodeURIComponent(raw)}catch{}
        if(!/\.(png|jpe?g|gif|webp|avif)$/i.test(name))return
        return name.replace(/[\u0000-\u001f\u007f]/g,'').slice(0,250)
    }catch{return undefined}
}
export function imageNames(message:any):string[]{
    const values:string[]=[]
    for(const a of (Array.isArray(message?.attachments)?message.attachments:[]).slice(0,20)){
        if(typeof a?.content_type==='string'&&!a.content_type.startsWith('image/'))continue
        const name=imageName(a?.url)
        if(name)values.push(name)
    }
    for(const e of (Array.isArray(message?.embeds)?message.embeds:[]).slice(0,10)){
        if(!['image','gifv'].includes(e?.type))continue
        const name=imageName(e.image?.url??e.thumbnail?.url??e.url)
        if(name)values.push(name)
    }
    return [...new Set(values)]
}
export function selectedSnapshot(kind:'Message'|'User'|'Channel'|'Guild',obj:any):Record<string,unknown>|undefined{
    if(!obj||!snowflake(obj.id))return
    const out:Record<string,unknown>={id:obj.id}
    const keys=kind==='Message'?['channel_id','guild_id','type','flags','content','timestamp','edited_timestamp']:
        kind==='User'?['username','global_name','discriminator','bot']:
        kind==='Channel'?['name','type','guild_id','topic','nsfw']:
        ['name','description','member_count','approximate_member_count','approximate_presence_count','verification_level','preferred_locale']
    for(const k of keys){
        const v=obj[k]
        if(typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v))out[k]=v
        else if(typeof v==='string')out[k]=v.slice(0,k==='content'?6000:500)
    }
    if(kind==='Message'&&obj.author&&snowflake(obj.author.id))
        out.author=selectedSnapshot('User',obj.author)
    const created=dateForId(obj.id)
    if(created)out.created=created
    return out
}
export function gifAltText(uri:unknown):string|undefined{
    const name=imageName(uri)
    if(!name||!name.toLowerCase().endsWith('.gif'))return
    const words=name.slice(0,-4).replace(/[0-9]+/g,'').split(/[,\-_ ]+/).filter(Boolean).slice(0,20).join(' ').trim()
    return words?'GIF - '+words.slice(0,240):undefined
}
export function replyTimestampRow(row:any):any{
    const ref=row?.message?.referencedMessage?.message
    if(row?.type!==1||!ref||typeof ref.username!=='string'||!ref.username||ref.username.length>160)return row
    const stamp=snowflakeTime(ref.id??ref.messageId)
    if(stamp===undefined)return row
    const date=new Date(stamp),base=new Date(snowflakeTime(row.message.id??row.message.messageId)??Date.now())
    const time=date.toDateString()===base.toDateString()?date.toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}):date.toLocaleDateString()
    const suffix=' ['+time+']'
    if(ref.username.endsWith(suffix))return row
    return {...row,message:{...row.message,referencedMessage:{...row.message.referencedMessage,message:{...ref,username:ref.username+suffix}}}}
}
export function ircColor(id:string,seed:string,lightness:number,saturation:number):number{
    let hash=2166136261
    for(const c of id+seed)hash=Math.imul(hash^c.charCodeAt(0),16777619)
    const h=(hash>>>0)%360/60,s=Math.max(0,Math.min(100,saturation))/100,l=Math.max(25,Math.min(85,lightness))/100
    const c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs(h%2-1)),m=l-c/2
    const rgb=h<1?[c,x,0]:h<2?[x,c,0]:h<3?[0,c,x]:h<4?[0,x,c]:h<5?[x,0,c]:[c,0,x]
    return rgb.reduce((v,a)=>v*256+Math.round((a+m)*255),0)
}
export function ircRow(row:any,p:Extract<RowProgram,{op:'rows.ircColors'}>,isDM?:boolean):any{
    const m=row?.message
    if(row?.type!==1||!m||!snowflake(m.authorId)||typeof m.username!=='string'||m.webhookId)return row
    if(p.onlyDMs&&isDM!==true||p.onlyUncolored&&(m.roleColor||m.usernameColor||m.colorString))return row
    const color=ircColor(m.authorId,p.seed,p.lightness,p.saturation)
    return {...row,message:{...m,roleColor:color,usernameColor:color,colorString:'#'+color.toString(16).padStart(6,'0')}}
}
export function enhanceGifAlt<T>(value:T):T{
    let budget=10000
    function visit(node:any,depth:number):any{
        if(--budget<0||depth>20||!node||typeof node!=='object')return node
        if(Array.isArray(node)){let changed=false;const result=node.map(x=>{const r=visit(x,depth+1);changed||=r!==x;return r});return changed?result:node}
        // Only improve existing accessible GIF labels, not arbitrary message text.
        const src=node.original??node.src??node.url
        const label=(node.alt==='GIF'||node.accessibilityLabel==='GIF')&&gifAltText(src)
        let next=node
        if(label){next={...node};if(node.alt==='GIF')next.alt=label;if(node.accessibilityLabel==='GIF')next.accessibilityLabel=label}
        for(const k of Object.keys(node)){if(k==='__proto__'||k==='prototype'||k==='constructor')continue;const v=visit(node[k],depth+1);if(v!==node[k]){if(next===node)next={...node};next[k]=v}}
        return next
    }
    return visit(value,0)
}

export function textAttachment(message:any,maxBytes=65536):{url:string;size:number;name:string}|undefined{
    const files=(Array.isArray(message?.attachments)?message.attachments:[]).filter((a:any)=>
        Number.isInteger(a?.size)&&a.size>=0&&a.size<=maxBytes&&typeof a.content_type==='string'&&
        (a.content_type.startsWith('text/')||/^application\/(?:json|xml)(?:;|$)/i.test(a.content_type))&&discordAttachmentUrl(a.url))
    if(files.length!==1)return
    return {url:files[0].url,size:files[0].size,name:String(files[0].filename??'Textdatei').slice(0,160)}
}
export function originalImage(message:any):string|undefined{
    const images=(Array.isArray(message?.attachments)?message.attachments:[]).filter((a:any)=>
        typeof a?.content_type==='string'&&a.content_type.startsWith('image/')&&discordAttachmentUrl(a.url))
    return images.length===1?images[0].url:undefined
}
export function publicImageUrl(message:any):string|undefined{
    // Never send signed Discord attachments, private/credential-bearing URLs
    // or URLs containing query strings to a third-party image search provider.
    const candidates=(Array.isArray(message?.embeds)?message.embeds:[]).filter((e:any)=>e?.type==='image')
        .map((e:any)=>e.image?.url??e.url)
    if(candidates.length!==1||typeof candidates[0]!=='string')return
    try{
        const u=new URL(candidates[0])
        if(u.protocol!=='https:'||u.port||u.username||u.password||u.search||u.hash||u.href.length>1500)return
        const h=u.hostname.toLowerCase()
        if(!/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(h)||/^(?:localhost|.*\.localhost|.*\.local|.*\.internal|.*\.lan|.*\.home|.*\.test|.*\.invalid|.*\.example)$/.test(h))return
        if(/(?:^|\.)discord(?:app|app\.net|app\.com|\.com|\.net)$/.test(h)||h==='cdn.discordapp.com'||h==='media.discordapp.net')return
        if(!imageName(u.href))return
        return u.href
    }catch{return undefined}
}
