import { readFileSync } from 'node:fs'
import { MongoClientFindOne, MongoClientInsertOne } from '#helpers/mongoclient.js'
import { compileRuleSet, type RuleSet } from '#helpers/rule-engine.js'
import type { ParserRulesDoc } from '#types/docs.js'

const ruleSet = JSON.parse(readFileSync(new URL('../../shared/parser-rules/rules.json', import.meta.url), 'utf8')) as RuleSet
compileRuleSet(ruleSet) // throws on an invalid regex before anything is written

const existing = await MongoClientFindOne<ParserRulesDoc>('parser_rules', { version: ruleSet.version })
if (existing.data) {
  console.log(`version ${ruleSet.version} already published`)
} else {
  const inserted = await MongoClientInsertOne<ParserRulesDoc>('parser_rules', {
    version: ruleSet.version,
    rules: ruleSet as unknown as Record<string, unknown>,
    published_at: new Date(),
  })
  console.log(inserted.status ? `published version ${ruleSet.version}` : `failed: ${inserted.message}`)
}
