import {describe,it,expect} from 'vitest'
import {discordAttachmentUrl,voiceFile,imageName,imageNames,textAttachment,originalImage,publicImageUrl,
    selectedSnapshot,latencyEstimate,replyTimestampRow,ircColor,ircRow,enhanceGifAlt} from './extra-operations'
const id=(t:number)=>((BigInt(t)-1420070400000n)<<22n).toString()
const messageId=id(1700000003000),authorId=id(1600000000000)
const media='https://cdn.discordapp.com/attachments/123/456/'
describe('Extended compiler portable operations',()=>{
    it('uses only selected Discord attachment URLs, preserving signatures',()=>{
        const url=media+'voice.ogg?ex=1&is=2&hm=abc'
        const m={flags:8192,attachments:[{url,filename:'voice.ogg',content_type:'audio/ogg'}]}
        expect(voiceFile(m)).toBe(url)
        expect(discordAttachmentUrl(url)).toBe(url)
        expect(discordAttachmentUrl('https://evil.example/attachments/123/456/a.txt')).toBeUndefined()
        expect(discordAttachmentUrl('https://cdn.discordapp.com@evil.example/attachments/123/456/a.txt')).toBeUndefined()
        expect(voiceFile({attachments:[{url,content_type:'audio/ogg'}]})).toBeUndefined()
    })
    it('selects only one bounded textual attachment',()=>{
        const a={url:media+'code.txt',filename:'code.txt',content_type:'text/plain',size:512}
        expect(textAttachment({attachments:[a]})?.size).toBe(512)
        expect(textAttachment({attachments:[a,a]})).toBeUndefined()
        expect(textAttachment({attachments:[{...a,size:70000}]})).toBeUndefined()
        expect(textAttachment({attachments:[{...a,content_type:'application/octet-stream'}]})).toBeUndefined()
    })
    it('keeps private attachment URLs out of external image searches',()=>{
        const message=(url:string)=>({embeds:[{type:'image',url}]})
        expect(publicImageUrl(message('https://images.example.com/photo.png'))).toBe('https://images.example.com/photo.png')
        expect(publicImageUrl(message(media+'photo.png'))).toBeUndefined()
        expect(publicImageUrl(message('https://images.example.com/photo.png?token=secret'))).toBeUndefined()
        expect(publicImageUrl(message('https://127.0.0.1/photo.png'))).toBeUndefined()
        expect(publicImageUrl(message('https://user:secret@images.example.com/photo.png'))).toBeUndefined()
        expect(publicImageUrl(message('https://device.local/photo.png'))).toBeUndefined()
        expect(originalImage({attachments:[{content_type:'image/png',url:media+'photo.png?hm=x'}]})).toBe(media+'photo.png?hm=x')
        expect(imageName(media+'some%20photo.png?hm=x')).toBe('some photo.png')
        expect(imageNames({attachments:[{content_type:'image/png',url:media+'photo.png'}]})).toEqual(['photo.png'])
    })
    it('redacts selected metadata to a fixed allowlist',()=>{
        const raw=selectedSnapshot('Message',{id:messageId,content:'fixture',token:'secret',author:{id:authorId,username:'Example',email:'private',phone:'private'}})!
        expect(raw.content).toBe('fixture')
        expect(raw).not.toHaveProperty('token')
        expect(raw.author).toEqual(expect.objectContaining({id:authorId,username:'Example'}))
        expect(raw.author).not.toHaveProperty('email')
        expect(raw.author).not.toHaveProperty('phone')
    })
    it('computes nonce differences, never calling them network RTT',()=>{
        expect(latencyEstimate(messageId,id(1700000000000))).toBe(3000)
        expect(latencyEstimate(messageId,'not-a-snowflake')).toBeUndefined()
        expect(latencyEstimate(messageId,id(1600000000000))).toBeUndefined()
    })
    it('transforms only loaded reply rows and preserves original objects',()=>{
        const row={type:1,message:{id:messageId,referencedMessage:{message:{id:id(1700000000000),username:'Example'}}}}
        const changed=replyTimestampRow(row)
        expect(changed.message.referencedMessage.message.username).toMatch(/^Example \[/)
        expect(row.message.referencedMessage.message.username).toBe('Example')
        expect(replyTimestampRow(changed)).toBe(changed)
        const absent={type:1,message:{}}
        expect(replyTimestampRow(absent)).toBe(absent)
    })
    it('uses deterministic local colors and preserves role color when configured',()=>{
        const p={op:'rows.ircColors' as const,lightness:70,saturation:100,seed:'',onlyUncolored:true,onlyDMs:false}
        const row={type:1,message:{authorId,username:'Example'}}
        const changed=ircRow(row,p)
        expect(changed.message.roleColor).toBe(ircColor(authorId,'',70,100))
        expect(changed.message.colorString).toMatch(/^#[0-9a-f]{6}$/)
        const colored={...row,message:{...row.message,roleColor:123}}
        expect(ircRow(colored,p)).toBe(colored)
        expect(ircRow(row,{...p,onlyDMs:true},false)).toBe(row)
    })
    it('only replaces generic GIF accessibility labels',()=>{
        const rows=[{alt:'GIF',src:'https://images.example.com/happy-cat123.gif'},{alt:'Existing',src:'https://images.example.com/happy-cat.gif'}]
        const changed=enhanceGifAlt(rows)
        expect(changed[0]!.alt).toBe('GIF - happy cat')
        expect(changed[1]).toBe(rows[1])
        expect(rows[0]!.alt).toBe('GIF')
    })
})
