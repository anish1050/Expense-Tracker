// Creates the database, every collection and index, then publishes the shared
// ruleset. Safe to run repeatedly.
import { getClientPromise, closeClient } from '#helpers/mongodb.js'
import { ensureIndexes } from '#helpers/indexes.js'

const COLLECTIONS = ['core_users', 'devices', 'sync_rows', 'parser_rules', 'request_logs'] as const

const client = await getClientPromise()
const db = client.db(process.env.MONGODB_DBNAME)
const existing = new Set((await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name))

for (const name of COLLECTIONS) {
  if (existing.has(name)) continue
  await db.createCollection(name)
  console.log(`created ${name}`)
}
await ensureIndexes()
console.log('indexes ensured')

await import('./publish-rules.js')
await closeClient()
