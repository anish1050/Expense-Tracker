import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import type { AnyBulkWriteOperation } from 'mongodb'
import { MongoClientBulkWrite, MongoClientFind, MongoClientUpdateOne } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { DeviceDoc, SyncRowDoc } from '#types/docs.js'

const MAX_ROWS = 500

const rowSchema = z
  .object({
    table: z.enum(['accounts', 'categories', 'budgets', 'transactions']),
    row_id: z.string().uuid(),
    version: z.number().int().positive(),
    updated_at: z.number().int().positive(),
    ciphertext: z.string().base64().max(16_384),
    nonce: z.string().base64().length(16),
    deleted: z.boolean(),
  })
  .strict()

const pushSchema = z
  .object({
    device_id: z.string().uuid(),
    platform: z.enum(['android', 'ios']).default('android'),
    rows: z.array(rowSchema).min(1).max(MAX_ROWS),
  })
  .strict()

const pullSchema = z
  .object({
    since: z.coerce.number().int().nonnegative().default(0),
    limit: z.coerce.number().int().min(1).max(MAX_ROWS).default(MAX_ROWS),
  })
  .strict()

export const syncRoutes = new Hono<{ Variables: AuthVariables }>()

syncRoutes.post('/push', zValidator('json', pushSchema, validationHook), async (c) => {
  const userId = c.get('userId')
  const { device_id: deviceId, platform, rows } = c.req.valid('json')

  try {
    // Only a strictly newer version may overwrite; upsert covers first sight.
    const ops: AnyBulkWriteOperation<SyncRowDoc>[] = rows.map((row) => ({
      updateOne: {
        filter: { _id: `${userId}:${row.row_id}`, version: { $lt: row.version } },
        update: {
          $set: {
            user_id: userId,
            table: row.table,
            row_id: row.row_id,
            version: row.version,
            updated_at: new Date(row.updated_at),
            ciphertext: row.ciphertext,
            nonce: row.nonce,
            deleted: row.deleted,
          },
        },
        upsert: true,
      },
    }))

    // Rows whose stored version is >= incoming fail the upsert with a duplicate
    // key error (11000); those are the rejects. ordered:false lets the rest land.
    const result = await MongoClientBulkWrite<SyncRowDoc>('sync_rows', ops)
    const bulk = result.data
    const rejectedIds = new Set<string>()
    if (!result.status) {
      const err = result as { error_code?: number | string }
      if (err.error_code !== 11000) return handleError(c, 'sync:push', result.message)
    }
    const accepted = bulk ? bulk.upsertedCount + bulk.modifiedCount : 0
    if (accepted < rows.length) {
      const stored = await MongoClientFind<SyncRowDoc>(
        'sync_rows',
        { _id: { $in: rows.map((r) => `${userId}:${r.row_id}`) } },
        { projection: { row_id: 1, version: 1 } },
      )
      const byId = new Map(stored.data.map((d) => [d.row_id, d.version]))
      for (const row of rows) {
        const serverVersion = byId.get(row.row_id)
        if (serverVersion !== undefined && serverVersion >= row.version) rejectedIds.add(row.row_id)
      }
      const rejected = [...rejectedIds].map((rowId) => ({ row_id: rowId, server_version: byId.get(rowId) ?? 0 }))
      void MongoClientUpdateOne<DeviceDoc>('devices', { _id: deviceId }, { $set: { user_id: userId, platform, last_seen_at: new Date() } }, { upsert: true })
      return successResponse(c, { accepted: rows.length - rejected.length, rejected })
    }

    void MongoClientUpdateOne<DeviceDoc>('devices', { _id: deviceId }, { $set: { user_id: userId, platform, last_seen_at: new Date() } }, { upsert: true })
    return successResponse(c, { accepted, rejected: [] })
  } catch (e) {
    return handleError(c, 'sync:push', e)
  }
})

syncRoutes.get('/pull', zValidator('query', pullSchema, validationHook), async (c) => {
  const userId = c.get('userId')
  const { since, limit } = c.req.valid('query')

  try {
    const found = await MongoClientFind<SyncRowDoc>(
      'sync_rows',
      { user_id: userId, updated_at: { $gt: new Date(since) } },
      { sort: { updated_at: 1 }, limit, projection: { _id: 0, user_id: 0 } },
    )
    const rows = found.data.map((r) => ({ ...r, updated_at: r.updated_at.getTime() }))
    const last = rows[rows.length - 1]
    return successResponse(c, { rows, next_since: last ? last.updated_at : since })
  } catch (e) {
    return handleError(c, 'sync:pull', e)
  }
})
