import { describe, expect, it } from 'vitest'
import { api } from './helpers.js'

describe('health', () => {
  it('returns ok', async () => {
    const res = await api('/api/health')
    expect(res.status).toBe(200)
    expect(await res.json()).toMatchObject({ status: 'ok' })
  })

  it('404s unknown routes with the envelope', async () => {
    const res = await api('/nope')
    expect(res.status).toBe(404)
    expect(await res.json()).toEqual({ success: false, error: 'Not Found' })
  })
})
