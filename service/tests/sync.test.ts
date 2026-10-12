import { beforeEach, describe, expect, it } from 'vitest'
import { randomUUID } from 'node:crypto'
import { api, json, newUserToken, wipeDb } from './helpers.js'

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
    expect(pull.data.next_since).toBe(2000)

    const again = await json(await api('/api/sync/pull?since=2000', {}, token))
    expect(again.data.rows).toEqual([])
    expect(again.data.next_since).toBe(2000)
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
