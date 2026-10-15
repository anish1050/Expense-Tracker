import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { ObjectId, type AnyBulkWriteOperation } from 'mongodb'
import { MongoClientBulkWrite, MongoClientFind, MongoClientFindOneAndUpdate, MongoClientUpdateOne } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { CoreUserDoc, DeviceDoc, SyncRowDoc } from '#types/docs.js'

const MAX_ROWS = 500
// Year 2100 in epoch ms; anything later is a broken device clock, not a real edit.
const MAX_UPDATED_AT = 4_102_444_800_000

const rowSchema = z
  .object({
    table: z.enum(['accounts', 'categories', 'budgets', 'transactions']),
    row_id: z.string().uuid(),
    version: z.number().int().positive(),
    updated_at: z.number().int().positive().max(MAX_UPDATED_AT),
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

// Each accepted row gets a server-assigned, per-user, strictly increasing seq.
// Pull pages by seq, so device clocks never decide ordering and a page boundary
// can never split rows that share a timestamp.
async function allocateSeq(userId: string, count: number): Promise<number> {
  const user = await MongoClientFindOneAndUpdate<CoreUserDoc>(
    'core_users',
    { _id: new ObjectId(userId) },
    { $inc: { sync_seq: count } },
    { returnDocument: 'after', projection: { sync_seq: 1 } },
  )
  if (!user.data) throw new Error('user not found for seq allocation')
  // Sequences start at 1 so a fresh client's since=0 pulls everything.
  return user.data.sync_seq - count + 1
}

syncRoutes.post('/push', zValidator('json', pushSchema, validationHook), async (c) => {
  const userId = c.get('userId')
  const { device_id: deviceId, platform, rows } = c.req.valid('json')

  try {
    const firstSeq = await allocateSeq(userId, rows.length)
    // Only a strictly newer version may overwrite; upsert covers first sight.
    const ops: AnyBulkWriteOperation<SyncRowDoc>[] = rows.map((row, i) => ({
      updateOne: {
        filter: { _id: `${userId}:${row.row_id}`, version: { $lt: row.version } },
        update: {
          $set: {
            user_id: userId,
            table: row.table,
            row_id: row.row_id,
            version: row.version,
            seq: firstSeq + i,
            updated_at: new Date(row.updated_at),
            ciphertext: row.ciphertext,
            nonce: row.nonce,
            deleted: row.deleted,
          },
        },
        upsert: true,
      },
    }))

    // A stored version >= incoming makes the filter miss and the upsert collide
    // on _id, which surfaces as a duplicate key for exactly that operation.
    const { duplicateKeyIndexes } = await MongoClientBulkWrite<SyncRowDoc>('sync_rows', ops)
    const rejectedRows = duplicateKeyIndexes.map((i) => rows[i]!)
    const rejected = rejectedRows.length === 0 ? [] : await serverVersions(userId, rejectedRows.map((r) => r.row_id))

    await MongoClientUpdateOne<DeviceDoc>(
      'devices',
      { _id: `${userId}:${deviceId}` },
      { $set: { user_id: userId, device_id: deviceId, platform, last_seen_at: new Date() } },
      { upsert: true },
    )
    return successResponse(c, { accepted: rows.length - rejected.length, rejected })
  } catch (e) {
    return handleError(c, 'sync:push', e)
  }
})

async function serverVersions(userId: string, rowIds: string[]) {
  const stored = await MongoClientFind<SyncRowDoc>(
    'sync_rows',
    { _id: { $in: rowIds.map((id) => `${userId}:${id}`) } },
    { projection: { row_id: 1, version: 1 } },
  )
  return stored.data.map((d) => ({ row_id: d.row_id, server_version: d.version }))
}

syncRoutes.get('/pull', zValidator('query', pullSchema, validationHook), async (c) => {
  const userId = c.get('userId')
  const { since, limit } = c.req.valid('query')

  try {
    const found = await MongoClientFind<SyncRowDoc>(
      'sync_rows',
      { user_id: userId, seq: { $gt: since } },
      { sort: { seq: 1 }, limit, projection: { _id: 0, user_id: 0 } },
    )
    const rows = found.data.map((r) => ({ ...r, updated_at: r.updated_at.getTime() }))
    const last = rows[rows.length - 1]
    return successResponse(c, { rows, next_since: last ? last.seq : since })
  } catch (e) {
    return handleError(c, 'sync:pull', e)
  }
})
