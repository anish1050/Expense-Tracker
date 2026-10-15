import { MongoClientCreateIndex } from './mongoclient.js'

let ensured: Promise<void> | null = null

export function ensureIndexes(): Promise<void> {
  if (!ensured) {
    ensured = Promise.all([
      MongoClientCreateIndex('core_users', { google_sub: 1 }, { unique: true }),
      MongoClientCreateIndex('sync_rows', { user_id: 1, seq: 1 }),
      MongoClientCreateIndex('devices', { user_id: 1 }),
      MongoClientCreateIndex('parser_rules', { version: -1 }, { unique: true }),
      MongoClientCreateIndex('request_logs', { created_at: 1 }, { expireAfterSeconds: 604800 }),
    ]).then(() => undefined)
    ensured.catch(() => {
      ensured = null
    })
  }
  return ensured
}
