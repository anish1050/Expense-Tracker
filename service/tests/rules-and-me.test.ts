import { beforeEach, describe, expect, it } from 'vitest'
import { randomUUID } from 'node:crypto'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { MongoClientFind, MongoClientInsertOne } from '#helpers/mongoclient.js'
import type { ParserRulesDoc, SyncRowDoc } from '#types/docs.js'

describe('GET /api/rules', () => {
  beforeEach(wipeDb)

  it('404s when nothing is published', async () => {
    expect((await api('/api/rules')).status).toBe(404)
  })

  it('returns the latest version and 304s when client is current', async () => {
    await MongoClientInsertOne<ParserRulesDoc>('parser_rules', { version: 1, rules: { version: 1 }, published_at: new Date() })
    await MongoClientInsertOne<ParserRulesDoc>('parser_rules', { version: 2, rules: { version: 2 }, published_at: new Date() })

    const res = await api('/api/rules')
    expect(res.status).toBe(200)
    expect((await json(res)).data.version).toBe(2)

    expect((await api('/api/rules?since_version=2')).status).toBe(304)
    expect((await api('/api/rules?since_version=1')).status).toBe(200)
  })
})

describe('DELETE /api/me', () => {
  beforeEach(wipeDb)

  it('removes the user data and is idempotent', async () => {
    const { token, userId } = await newUserToken()
    await api('/api/sync/push', {
      method: 'POST',
      body: JSON.stringify({ device_id: randomUUID(), rows: [{ table: 'budgets', row_id: randomUUID(), version: 1, updated_at: 1, ciphertext: 'YQ==', nonce: Buffer.alloc(12).toString('base64'), deleted: false }] }),
    }, token)
    expect((await MongoClientFind<SyncRowDoc>('sync_rows', { user_id: userId })).data).toHaveLength(1)

    expect((await api('/api/me', { method: 'DELETE' }, token)).status).toBe(204)
    expect((await MongoClientFind<SyncRowDoc>('sync_rows', { user_id: userId })).data).toHaveLength(0)
    expect((await api('/api/me', { method: 'DELETE' }, token)).status).toBe(204)
  })
})
