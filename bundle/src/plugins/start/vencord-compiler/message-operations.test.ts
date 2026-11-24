import {describe,it,expect} from 'vitest'
import {applyReplacementRules,boundedRegex,convertTimeShortcuts,silentPrefix,timestampTag} from './operations'

describe('Source-derived mobile message IR',()=>{
    it('applies ordered literal rules with conditional matching and source exclusions',()=>{
        const rules=[{find:'brb',replace:'be right back',onlyIfIncludes:'hey'},{find:'right',replace:'right\\nnow'}]
        expect(applyReplacementRules('hey brb',rules,'other','excluded')).toBe('hey be right\nnow back')
        expect(applyReplacementRules('brb',rules,'other','excluded')).toBe('brb')
        expect(applyReplacementRules('hey brb',rules,'excluded','excluded')).toBe('hey brb')
        expect(applyReplacementRules('  no changes  ',[],'other','excluded')).toBe('  no changes  ')
    })
    it('permits bounded regex and rejects costly or unsupported expressions',()=>{
        expect(boundedRegex('/[a-z]\\d/gi')?.test('X7')).toBe(true)
        for(const value of ['(a+)+','a*','a{1,20}','a|b','(a)','\\1','(?=x)','/x/y','[a-z'])
            expect(boundedRegex(value)).toBeUndefined()
        expect(applyReplacementRules('X7 Y8',[{find:'/[A-Z]\\d/g',replace:'ok',regex:true}],'','excluded')).toBe('ok ok')
    })
    it('makes local-time timestamp tags, handling next-day and AM/PM',()=>{
        const now=new Date(2026,10,24,15,30,0)
        const expected=(day:number,hour:number,min:number)=>'<t:'+Math.round(new Date(2026,10,day,hour,min,0).getTime()/1000)+':t>'
        expect(timestampTag('18:45',now)).toBe(expected(24,18,45))
        expect(timestampTag('8:00 AM',now)).toBe(expected(25,8,0))
        expect(timestampTag('12:00 PM',now)).toBe(expected(25,12,0))
        expect(timestampTag('24:00',now)).toBe(expected(25,0,0))
        expect(timestampTag('24:15',now)).toBeUndefined()
        expect(timestampTag('25:00',now)).toBeUndefined()
        const ticks=String.fromCharCode(96)
        const sample=ticks+'18:45'+ticks
        const fenced=ticks.repeat(3)+'\n'+sample+'\n'+ticks.repeat(3)
        expect(convertTimeShortcuts('Meet '+sample+'\n'+fenced,now)).toBe('Meet '+expected(24,18,45)+'\n'+fenced)
    })
    it('prefixes only nonempty user content and never duplicates the prefix',()=>{
        expect(silentPrefix('hello','@silent ')).toBe('@silent hello')
        expect(silentPrefix('@silent hello','@silent ')).toBe('@silent hello')
        expect(silentPrefix('  ','@silent ')).toBe('  ')
    })
})
