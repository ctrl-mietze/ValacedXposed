import {describe,it,expect} from 'vitest'
import {imageLinks,pastelRow,pastelColor,animatedAvatar,animateRow,cleanText,cleanUrl} from './transforms'
describe('Veyra Revenge native ports',()=>{
    it('adds deduplicated image links only for a media-only message',()=>{
        const row={type:1,message:{content:[],embeds:[{type:'image',url:'https://example.org/a.png'},{type:'gifv',url:'https://example.org/a.png'},{type:'image',url:'file:///x'}]}}
        const r=imageLinks(row);expect(r.message.content).toHaveLength(1);expect(r.message.content[0].target).toBe('https://example.org/a.png');expect(row.message.content).toHaveLength(0)
        expect(imageLinks({type:2,message:row.message}).message).toBe(row.message)
    })
    it('preserves role colors and creates stable pastel colors',()=>{
        const m={type:1,message:{authorId:'123456789',username:'A'}}
        expect(pastelRow(m).message.roleColor).toBe(pastelColor('123456789'))
        const colored={type:1,message:{...m.message,roleColor:123}}
        expect(pastelRow(colored)).toBe(colored)
    })
    it('animates existing animated Discord assets only',()=>{
        const url='https://cdn.discordapp.com/avatars/42/a_abc123.webp?size=128'
        expect(animatedAvatar(url)).toBe('https://cdn.discordapp.com/avatars/42/a_abc123.gif?size=128')
        expect(animatedAvatar('https://example.org/a_abc123.webp')).toBe('https://example.org/a_abc123.webp')
        expect(animateRow({type:1,message:{avatarURL:url}}).message.avatarURL).toContain('.gif')
    })
    it('strips only known tracking parameters without changing signed links',()=>{
        expect(cleanUrl('https://example.org/?a=one%20two&utm_source=x&b=2#here')).toBe('https://example.org/?a=one%20two&b=2#here')
        const signed='https://cdn.discordapp.com/attachments/a?ex=123&hm=abc&utm_source=x'
        expect(cleanUrl(signed)).toBe(signed)
        expect(cleanText('Link https://example.org/?fbclid=x.')).toBe('Link https://example.org/.')
        expect(cleanText('plain')).toBe('plain')
    })
})
