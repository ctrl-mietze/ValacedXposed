import { describe, expect, it } from 'vitest'
import { palettes, ownTheme, previewPalette, previewTokens, contrast, onColor, brandFill } from './palettes'
describe('Veyra Discord palettes',()=>{
    it('preserves stable preset IDs and gives each a complete role preview',()=>{
        expect(palettes.map(p=>p.id)).toEqual(['midnight','lavender','frost','neon','glass','amoled','bloom','eclipse'])
        for(const p of palettes)expect(previewPalette(p)).toHaveLength(previewTokens.length)
    })
    it('maps chat, header, input, navigation and elevated surfaces separately',()=>{
        for(const p of palettes){
            const theme=ownTheme(p), c=theme.colors.semantic.dark
            expect(c.BACKGROUND_BASE_LOWER).toBe(p.chat)
            expect(c.BACKGROUND_BASE_LOW).toBe(p.header)
            expect(c.CHANNEL_BACKGROUND_DEFAULT).toBe(p.sidebar)
            expect(c.MOBILE_FLOATINGBAR_BACKGROUND).toBe(p.input)
            expect(c.MOBILE_FLOATINGBAR_BACKGROUND_HIGHER).toBe(p.input)
            expect(c.BACKGROUND_FLOATING).toBe(p.elevated)
            expect(c.TEXT_LINK).toBe(p.accent)
            expect(c.BACKGROUND_MODIFIER_SELECTED).toBe(p.selected)
            expect(c.TEXT_ON_BRAND).toBe(onColor(brandFill(p)))
            expect(theme.colors.semantic.midnight).toEqual(c)
            expect(theme.colors.raw).not.toHaveProperty('PRIMARY_600')
            expect(c).not.toHaveProperty('TEXT_FEEDBACK_CRITICAL')
            expect(new Set([p.chat,p.sidebar,p.header,p.input,p.elevated]).size).toBe(5)
        }
    })
    it('keeps small text, links and button labels readable',()=>{
        for(const p of palettes){
            for(const bg of [p.chat,p.sidebar,p.header,p.input,p.elevated,p.selected]){
                expect(contrast(p.text,bg)).toBeGreaterThanOrEqual(4.5)
                expect(contrast(p.secondary,bg)).toBeGreaterThanOrEqual(4.5)
            }
            expect(contrast(p.muted,p.chat)).toBeGreaterThanOrEqual(4.5)
            expect(contrast(p.accent,p.chat)).toBeGreaterThanOrEqual(4.5)
            expect(contrast(onColor(p.accent),p.accent)).toBeGreaterThanOrEqual(4.5)
            expect(contrast('#ffffff',brandFill(p))).toBeGreaterThanOrEqual(4.5)
        }
    })
})
