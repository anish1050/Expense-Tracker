// Mirror of the Kotlin RuleEngine. Exists so rules.json is validated against
// the shared fixtures before publish; the server never parses user text.

export type Source = 'manual' | 'notification' | 'sms' | 'gallery'
export type Direction = 'debit' | 'credit'

export interface Rule {
  id: string
  source: Source | 'any'
  match: string
  direction: Direction
  confidence: number
}

export interface RuleSet {
  version: number
  notification_packages: string[]
  sms_sender_patterns: string[]
  rules: Rule[]
}

export interface RawCapture {
  source: Source
  sender: string
  text: string
  received_at: number
}

export interface ParsedTransaction {
  rule_id: string
  direction: Direction
  amount_minor: number
  merchant: string
  last4?: string
  ref?: string
  confidence: number
}

interface CompiledRule extends Rule {
  regex: RegExp
}

export function compileRuleSet(ruleSet: RuleSet) {
  const rules: CompiledRule[] = ruleSet.rules.map((r) => ({ ...r, regex: toJsRegex(r.match) }))
  const senders = ruleSet.sms_sender_patterns.map((p) => new RegExp(p))
  const packages = new Set(ruleSet.notification_packages)

  return {
    match(raw: RawCapture): ParsedTransaction | null {
      if (raw.source === 'sms' && !senders.some((s) => s.test(raw.sender))) return null
      if (raw.source === 'notification' && !packages.has(raw.sender)) return null
      for (const rule of rules) {
        if (rule.source !== 'any' && rule.source !== raw.source) continue
        const m = rule.regex.exec(raw.text)
        if (!m?.groups?.amount) continue
        return {
          rule_id: rule.id,
          direction: rule.direction,
          amount_minor: toMinor(m.groups.amount),
          merchant: (m.groups.merchant ?? '').trim(),
          ...(m.groups.last4 ? { last4: m.groups.last4 } : {}),
          ...(m.groups.ref ? { ref: m.groups.ref } : {}),
          confidence: rule.confidence,
        }
      }
      return null
    },
  }
}

// Rules are written in Java/Kotlin regex syntax; inline flags like (?i)/(?is)
// are moved to JS flags. Everything else used in rules.json is common syntax.
function toJsRegex(pattern: string): RegExp {
  const inline = /^\(\?([a-z]+)\)/.exec(pattern)
  let flags = 'u'
  let body = pattern
  if (inline?.[1]) {
    const f = inline[1]
    if (f.includes('i')) flags += 'i'
    if (f.includes('s')) flags += 's'
    body = pattern.slice(inline[0].length)
  }
  return new RegExp(body, flags)
}

// "1,23,456.70" (Indian), "1,234.56" (US) and "1.234,56" (European) all parse
// to the same minor units: a comma followed by exactly two digits at the end is
// a decimal separator, every other comma or dot in a group position is grouping.
export function toMinor(amount: string): number {
  const european = /^\d{1,3}(?:\.\d{3})*,\d{1,2}$/.test(amount)
  const cleaned = european ? amount.replace(/\./g, '').replace(',', '.') : amount.replace(/,/g, '')
  const [whole = '0', frac = ''] = cleaned.split('.')
  return Number(whole) * 100 + Number((frac + '00').slice(0, 2))
}
