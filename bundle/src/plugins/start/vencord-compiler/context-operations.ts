/** Portable operations used by the context menu and reply-policy backend. */
export type ContextProgram=
    | {op:'menu.userUrl';label:string;template:'https://discord.com/users/'}
    | {op:'menu.emojiMarkdown';label:string;copyUnicodeDefault:boolean}
    | {op:'reply.mentionPolicy';pingListedDefault:boolean}

export function snowflake(value:unknown):value is string{
    return typeof value==='string'&&/^\d{16,22}$/.test(value)
}
export function userProfileLink(id:unknown):string|undefined{
    return snowflake(id)?'<https://discord.com/users/'+id+'>':undefined
}
export function emojiMarkdown(id:unknown,name:unknown,animated:unknown=false):string|undefined{
    if(!snowflake(id)||typeof name!=='string')return
    const clean=name.replace(/~\d+$/,'')
    if(!/^[A-Za-z0-9_]{1,64}$/.test(clean))return
    return '<'+(animated===true?'a':'')+':'+clean+':'+id+'>'
}
export function idSet(value:unknown):Set<string>{
    return new Set(typeof value==='string'?value.slice(0,4096).split(/[\s,]+/).filter(snowflake).slice(0,100):[])
}
export function replyMentionPolicy(original:boolean,author:unknown,roles:unknown,users:string,roleIds:string,pingListed:boolean):boolean{
    const userSet=idSet(users),roleSet=idSet(roleIds)
    const listed=snowflake(author)&&userSet.has(author)||Array.isArray(roles)&&roles.some(r=>snowflake(r)&&roleSet.has(r))
    // A default that was already silent must not become a mention.
    return original&&(pingListed?listed:!listed)
}
