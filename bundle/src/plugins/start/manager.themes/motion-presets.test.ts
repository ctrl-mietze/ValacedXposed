import {describe,it,expect} from 'vitest'
import {motionPresets,motionById,motionIdFromUri} from './motion-presets'
import {ownTheme,contrast,previewPalette} from './palettes'
describe('Veyra Motion Studio',()=>{
    it('preserves old IDs and includes nine distinct additions',()=>{
        expect(motionPresets).toHaveLength(12)
        expect(new Set(motionPresets.map(p=>p.id)).size).toBe(12)
        for(const id of ['aurora','bloom','eclipse','crimson','rose','purple','ruby','sapphire','emerald','solar','obsidian','sunset'])expect(motionById.has(id)).toBe(true)
    })
    it('accepts only locally generated supported background paths',()=>{
        for(const p of motionPresets){
            expect(motionIdFromUri('file:///data/user/0/com.discord/files/valenced/manager-theme-assets/veyra-motion-'+p.id+'-v2.png')).toBe(p.id)
        }
        expect(motionIdFromUri('file:///data/user/0/com.discord/files/valenced/manager-theme-assets/veyra-motion-eclipse.png')).toBe('eclipse')
        expect(motionIdFromUri('https://example.org/valenced/manager-theme-assets/veyra-motion-ruby-v2.png')).toBeUndefined()
        expect(motionIdFromUri('file:///tmp/veyra-motion-ruby.png')).toBeUndefined()
        expect(motionIdFromUri('file:///data/user/0/com.discord/files/valenced/manager-theme-assets/veyra-motion-unknown.png')).toBeUndefined()
    })
    it('preserves readable surfaces and palette structure',()=>{
        for(const p of motionPresets){
            const c=p.palette
            expect(previewPalette(c)).toHaveLength(11)
            expect(ownTheme(c).colors.semantic.dark.CHANNEL_BACKGROUND_DEFAULT).toBe(c.chat)
            for(const bg of [c.chat,c.sidebar,c.header,c.input,c.elevated,c.selected]){
                expect(contrast(c.text,bg)).toBeGreaterThanOrEqual(4.5)
                expect(contrast(c.secondary,bg)).toBeGreaterThanOrEqual(4.5)
            }
            expect(contrast(c.accent,c.chat)).toBeGreaterThanOrEqual(4.5)
            expect(p.duration).toBeGreaterThan(4000)
            expect(p.dim).toBeLessThanOrEqual(.7)
        }
    })
})
