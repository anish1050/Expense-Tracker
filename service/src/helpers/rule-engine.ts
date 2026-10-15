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
  const senders = ruleSet.sms_sender_patterns.map(toJsRegex)
  const packages = new Set(ruleSet.notification_packages)

  return {
    match(raw: RawCapture): ParsedTransaction | null {
      if (raw.source === 'sms' && !senders.some((s) => s.test(raw.sender))) return null
      if (raw.source === 'notification' && !packages.has(raw.sender)) return null
      for (const rule of rules) {
        if (rule.source !== 'any' && rule.source !== raw.source) continue
        const m = rule.regex.exec(raw.text)
        if (!m?.groups?.amount) continue
        let amountMinor: number
        try {
          amountMinor = toMinor(m.groups.amount)
        } catch {
          continue
        }
        if (amountMinor <= 0) continue
        return {
          rule_id: rule.id,
          direction: rule.direction,
          amount_minor: amountMinor,
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

// "1,23,456.70" (Indian), "1,234.56" (US), "1.234,56" and "1.234" (European)
// all parse to the same minor units. A trailing comma plus 1-2 digits is a
// decimal separator; dots that only ever sit in groups of three are grouping.
// Mirrors Money.parseToMinor in the Android app.
export function toMinor(amount: string): number {
  const decimalComma = /^\d{1,3}(?:\.\d{3})*,\d{1,2}$/.test(amount)
  const dotGrouped = /^\d{1,3}(?:\.\d{3})+$/.test(amount)
  const cleaned = decimalComma ? amount.replace(/\./g, '').replace(',', '.') : dotGrouped ? amount.replace(/\./g, '') : amount.replace(/,/g, '')
  const [whole = '0', frac = ''] = cleaned.split('.')
  if (frac.length > 2 || whole.length > 15) throw new RangeError(`unparseable amount ${amount}`)
  return Number(whole) * 100 + Number((frac + '00').slice(0, 2))
}
