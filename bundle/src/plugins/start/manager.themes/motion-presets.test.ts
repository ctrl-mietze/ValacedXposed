import {describe,it,expect} from 'vitest'
import {motionPresets,motionById,motionIdFromUri,ambientSource,ambientDuration,ambientOpacity} from './motion-presets'
import {ownTheme,contrast,previewPalette} from './palettes'
describe('Veyra Motion Studio',()=>{
    it('preserves old IDs and includes Ruby Violet',()=>{
        expect(motionPresets).toHaveLength(13)
        expect(new Set(motionPresets.map(p=>p.id)).size).toBe(13)
        for(const id of ['aurora','bloom','eclipse','crimson','rose','purple','ruby','sapphire','emerald','solar','obsidian','sunset','ruby-violet'])expect(motionById.has(id)).toBe(true)
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
    it('provides an independent ambient layer and supports old backgrounds',()=>{
        const root='file:///data/user/0/com.discord/files/valenced/manager-theme-assets/'
        for(const p of motionPresets){
            const uri=root+'veyra-motion-'+p.id+'-v3.png'
            expect(motionIdFromUri(uri)).toBe(p.id)
            expect(ambientSource(uri)).toBe(uri.slice(0,-4)+'-ambient.png')
            expect(motionIdFromUri(ambientSource(uri))).toBeUndefined()
            const old=root+'veyra-motion-'+p.id+'-v2.png'
            expect(ambientSource(old)).toBe(old)
            expect(ambientDuration(p)).not.toBe(p.duration*2)
        }
        expect(ambientOpacity(0,1)).toBe(0)
        expect(ambientOpacity(.5,1)).toBeCloseTo(.22)
        expect(ambientOpacity(1,0)).toBeCloseTo(.24)
        expect(ambientDuration(motionById.get('ruby-violet')!)).toBe(12400)
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
