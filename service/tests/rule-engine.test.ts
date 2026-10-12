import { readFileSync, readdirSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { compileRuleSet, toMinor, type RawCapture, type RuleSet } from '#helpers/rule-engine.js'

const base = new URL('../../shared/parser-rules/', import.meta.url)
const ruleSet = JSON.parse(readFileSync(new URL('rules.json', base), 'utf8')) as RuleSet
const engine = compileRuleSet(ruleSet)

interface Fixture {
  rule: string | null
  source: RawCapture['source']
  sender: string
  text: string
  expect: Record<string, unknown> | null
}

const fixtures = readdirSync(new URL('fixtures/', base))
  .filter((f) => f.endsWith('.json'))
  .flatMap((f) => JSON.parse(readFileSync(new URL(`fixtures/${f}`, base), 'utf8')) as Fixture[])

describe('shared parser fixtures', () => {
  it.each(fixtures.map((f) => [f.rule ?? `no-match: ${f.text.slice(0, 30)}`, f] as const))('%s', (_name, fixture) => {
    const parsed = engine.match({ source: fixture.source, sender: fixture.sender, text: fixture.text, received_at: 0 })
    if (fixture.expect === null) {
      expect(parsed).toBeNull()
      return
    }
    expect(parsed).not.toBeNull()
    expect(parsed?.rule_id).toBe(fixture.rule)
    expect(parsed).toMatchObject(fixture.expect)
  })

  it('parses amounts into minor units without floats', () => {
    expect(toMinor('1,23,456.7')).toBe(12345670)
    expect(toMinor('75.0')).toBe(7500)
    expect(toMinor('120')).toBe(12000)
    expect(toMinor('1.234,56')).toBe(123456)
    expect(toMinor('89,90')).toBe(8990)
  })
})
