import { beforeEach, describe, expect, it } from 'vitest'
import { randomUUID } from 'node:crypto'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { MongoClientFind } from '#helpers/mongoclient.js'
import type { DeviceDoc } from '#types/docs.js'

const nonce = Buffer.alloc(12, 1).toString('base64')
const ct = Buffer.from('opaque').toString('base64')

function row(rowId: string, version: number, updatedAt: number) {
  return { table: 'transactions', row_id: rowId, version, updated_at: updatedAt, ciphertext: ct, nonce, deleted: false }
}

async function push(token: string, rows: unknown[]) {
  const res = await api('/api/sync/push', { method: 'POST', body: JSON.stringify({ device_id: randomUUID(), rows }) }, token)
  return { status: res.status, body: await json(res) }
}

describe('sync', () => {
  beforeEach(wipeDb)

  it('pushes new rows and pulls them back', async () => {
    const { token } = await newUserToken()
    const a = randomUUID()
    const b = randomUUID()
    const pushed = await push(token, [row(a, 1, 1000), row(b, 1, 2000)])
    expect(pushed.status).toBe(200)
    expect(pushed.body.data).toEqual({ accepted: 2, rejected: [] })

    const pull = await json(await api('/api/sync/pull?since=0', {}, token))
    expect(pull.data.rows.map((r: { row_id: string }) => r.row_id)).toEqual([a, b])
    expect(pull.data.rows.map((r: { seq: number }) => r.seq)).toEqual([1, 2])
    expect(pull.data.next_since).toBe(2)

    const again = await json(await api('/api/sync/pull?since=2', {}, token))
    expect(again.data.rows).toEqual([])
    expect(again.data.next_since).toBe(2)
  })

  it('rejects stale versions and accepts newer ones', async () => {
    const { token } = await newUserToken()
    const a = randomUUID()
    await push(token, [row(a, 3, 3000)])

    const stale = await push(token, [row(a, 2, 3500)])
    expect(stale.body.data).toEqual({ accepted: 0, rejected: [{ row_id: a, server_version: 3 }] })

    const newer = await push(token, [row(a, 4, 4000)])
    expect(newer.body.data).toEqual({ accepted: 1, rejected: [] })
  })

  it('reports only the stale rows of a mixed batch as rejected', async () => {
    const { token } = await newUserToken()
    const stale = randomUUID()
    const fresh = randomUUID()
    await push(token, [row(stale, 3, 1000)])

    const mixed = await push(token, [row(fresh, 1, 2000), row(stale, 2, 2500)])
    expect(mixed.body.data).toEqual({ accepted: 1, rejected: [{ row_id: stale, server_version: 3 }] })
  })

  it('pages by server sequence even when timestamps collide', async () => {
    const { token } = await newUserToken()
    const rows = Array.from({ length: 3 }, () => row(randomUUID(), 1, 5000))
    await push(token, rows)

    const first = await json(await api('/api/sync/pull?since=0&limit=2', {}, token))
    expect(first.data.rows).toHaveLength(2)
    const second = await json(await api(`/api/sync/pull?since=${first.data.next_since}&limit=2`, {}, token))
    expect(second.data.rows).toHaveLength(1)
    const seen = [...first.data.rows, ...second.data.rows].map((r: { row_id: string }) => r.row_id).sort()
    expect(seen).toEqual(rows.map((r) => r.row_id).sort())
  })

  it('rejects updated_at beyond the year 2100', async () => {
    const { token } = await newUserToken()
    expect((await push(token, [row(randomUUID(), 1, 9e15)])).status).toBe(400)
  })

  it('keeps device records per user', async () => {
    const alice = await newUserToken()
    const bob = await newUserToken()
    const deviceId = randomUUID()
    const send = (token: string) => api('/api/sync/push', { method: 'POST', body: JSON.stringify({ device_id: deviceId, rows: [row(randomUUID(), 1, 1)] }) }, token)
    await send(alice.token)
    await send(bob.token)
    const devices = await MongoClientFind<DeviceDoc>('devices', { device_id: deviceId })
    expect(devices.data.map((d) => d.user_id).sort()).toEqual([alice.userId, bob.userId].sort())
  })

  it('isolates users', async () => {
    const alice = await newUserToken()
    const bob = await newUserToken()
    await push(alice.token, [row(randomUUID(), 1, 1000)])
    const pull = await json(await api('/api/sync/pull', {}, bob.token))
    expect(pull.data.rows).toEqual([])
  })

  it('caps the batch at 500 rows and validates shape', async () => {
    const { token } = await newUserToken()
    const tooMany = Array.from({ length: 501 }, (_, i) => row(randomUUID(), 1, i + 1))
    expect((await push(token, tooMany)).status).toBe(400)
    expect((await push(token, [{ ...row(randomUUID(), 1, 1), nonce: 'short' }])).status).toBe(400)
  })
})
