import {describe,it,expect} from 'vitest'
import {normalizeCodeFences,removeIndent,normalizeRenderedRows} from './operations'
describe('Vencord compiler portable operations',()=>{
    it('removes shared indentation while preserving nesting',()=>{
        expect(removeIndent('    alpha\n        beta\n    gamma',4)).toBe('alpha\n    beta\ngamma')
        expect(removeIndent('\talpha\n\t\tbeta',4)).toBe('alpha\n    beta')
        expect(removeIndent('alpha\n  beta',4)).toBe('alpha\n  beta')
    })
    it('only transforms fenced multiline code',()=>{
        const src='hello \x60inline\x60\n\x60\x60\x60js\n    foo()\n        bar()\n\x60\x60\x60\nworld'
        expect(normalizeCodeFences(src,4)).toBe('hello \x60inline\x60\n\x60\x60\x60js\nfoo()\n    bar()\n\x60\x60\x60\nworld')
        expect(normalizeCodeFences('  plain text',4)).toBe('  plain text')
    })
    it('does not mutate input or unrelated nodes',()=>{
        const rows=[{message:{content:[{type:'codeBlock',lang:'js',content:'  foo\n    bar'},{type:'text',content:'  unchanged'}]}}]
        const result=normalizeRenderedRows(rows,4)
        expect(result[0]!.message.content[0]!.content).toBe('foo\n  bar')
        expect(rows[0]!.message.content[0]!.content).toBe('  foo\n    bar')
        expect(result[0]!.message.content[1]).toBe(rows[0]!.message.content[1])
    })
})
