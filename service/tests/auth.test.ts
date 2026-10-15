import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, json, wipeDb } from './helpers.js'
import { MongoClientFind } from '#helpers/mongoclient.js'
import type { CoreUserDoc } from '#types/docs.js'

vi.mock('#helpers/jwt.js', async (importOriginal) => {
  const real = await importOriginal<typeof import('#helpers/jwt.js')>()
  return {
    ...real,
    verifyGoogleIdToken: vi.fn(async (token: string) => {
      if (token !== 'valid-google-id-token-xxxxxxxx') throw new real.GoogleTokenError('bad token')
      return { sub: 'google-sub-1', email: 'a@example.com', name: 'A' }
    }),
  }
})

const body = (idToken: string) => ({ method: 'POST', body: JSON.stringify({ id_token: idToken }) })

describe('POST /api/auth/google', () => {
  beforeEach(wipeDb)

  it('rejects an invalid google token', async () => {
    const res = await api('/api/auth/google', body('invalid-token-xxxxxxxxxxxxxxx'))
    expect(res.status).toBe(401)
  })

  it('rejects unknown body fields', async () => {
    const res = await api('/api/auth/google', { method: 'POST', body: JSON.stringify({ id_token: 'valid-google-id-token-xxxxxxxx', extra: 1 }) })
    expect(res.status).toBe(400)
  })

  it('creates the user once and returns a usable jwt', async () => {
    const first = await api('/api/auth/google', body('valid-google-id-token-xxxxxxxx'))
    expect(first.status).toBe(200)
    const { data } = await json(first)
    expect(data.user.email).toBe('a@example.com')

    const second = await api('/api/auth/google', body('valid-google-id-token-xxxxxxxx'))
    expect((await json(second)).data.user.id).toBe(data.user.id)

    const users = await MongoClientFind<CoreUserDoc>('core_users', {})
    expect(users.data).toHaveLength(1)

    const me = await api('/api/me', {}, data.access_token)
    expect(me.status).toBe(200)
    expect((await json(me)).data.id).toBe(data.user.id)
  })

  it('401s protected routes without a token', async () => {
    expect((await api('/api/me')).status).toBe(401)
    expect((await api('/api/me', {}, 'garbage')).status).toBe(401)
  })

  it('401s a valid token whose account was deleted', async () => {
    const first = await api('/api/auth/google', body('valid-google-id-token-xxxxxxxx'))
    const { data } = await json(first)
    expect((await api('/api/me', { method: 'DELETE' }, data.access_token)).status).toBe(204)
    expect((await api('/api/me', {}, data.access_token)).status).toBe(401)
  })
})
