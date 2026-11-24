import {describe,it,expect} from 'vitest'
import {emojiMarkdown,userProfileLink,idSet,replyMentionPolicy} from './context-operations'
import {messageText,changeMessageText} from './message-payload'

describe('Mobile message ABI',()=>{
    it('handles string and record payloads without losing metadata',()=>{
        const record={content:'hello',nonce:'123',attachments:[{id:'1'}],tts:false}
        const original=['channel',record,true,{location:'chat'}]
        const result=changeMessageText(original,1,x=>x+'!')
        expect(result[1]).toEqual({...record,content:'hello!'})
        expect(result[1].attachments).toBe(record.attachments)
        expect(result[3]).toBe(original[3])
        expect(original[1]).toBe(record)
        expect(record.content).toBe('hello')
        expect(changeMessageText(['channel','hello'],1,x=>x+'!')).toEqual(['channel','hello!'])
        expect(changeMessageText(original,1,x=>x)).toBe(original)
    })
    it('does not transform non-message arguments',()=>{
        for(const value of [null,undefined,42,{},[],{content:42},new Date()])
            expect(messageText(value)).toBeUndefined()
        const getter=Object.defineProperty({},'content',{get(){throw Error('must not execute getter')}})
        expect(messageText(getter)).toBeUndefined()
    })
})
describe('Context menu compiler operations',()=>{
    const user='123456789012345678',role='987654321098765432'
    it('constructs only valid Discord profile URLs',()=>{
        expect(userProfileLink(user)).toBe('<https://discord.com/users/'+user+'>')
        expect(userProfileLink('../bad')).toBeUndefined()
        expect(userProfileLink(123)).toBeUndefined()
    })
    it('creates only valid custom emoji markdown',()=>{
        expect(emojiMarkdown(user,'test~2',true)).toBe('<a:test:'+user+'>')
        expect(emojiMarkdown(user,'test',false)).toBe('<:test:'+user+'>')
        expect(emojiMarkdown('bad','test')).toBeUndefined()
        expect(emojiMarkdown(user,'bad:evil')).toBeUndefined()
    })
    it('uses exact IDs and never turns a silent reply into a ping',()=>{
        expect([...idSet(user+', '+role+' bad')]).toEqual([user,role])
        expect(replyMentionPolicy(true,user,[],user,'',true)).toBe(true)
        expect(replyMentionPolicy(true,user,[role],'',role,true)).toBe(true)
        expect(replyMentionPolicy(true,user,[],'','',true)).toBe(false)
        expect(replyMentionPolicy(true,user,[],user,'',false)).toBe(false)
        expect(replyMentionPolicy(false,user,[],user,'',true)).toBe(false)
    })
})
