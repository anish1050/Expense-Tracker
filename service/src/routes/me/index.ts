import { Hono } from 'hono'
import { ObjectId } from 'mongodb'
import { MongoClientDeleteMany } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { CoreUserDoc, DeviceDoc, SyncRowDoc } from '#types/docs.js'

export const meRoutes = new Hono<{ Variables: AuthVariables }>()

meRoutes.get('/', (c) => successResponse(c, { id: c.get('userId') }))

// Google Play account-deletion requirement. Idempotent: deleting twice is a
// no-op. The helpers throw on any database error, so a failed wipe is a 500
// and the client keeps the account until it really is gone.
meRoutes.delete('/', async (c) => {
  const userId = c.get('userId')
  try {
    await Promise.all([
      MongoClientDeleteMany<SyncRowDoc>('sync_rows', { user_id: userId }),
      MongoClientDeleteMany<DeviceDoc>('devices', { user_id: userId }),
    ])
    await MongoClientDeleteMany<CoreUserDoc>('core_users', { _id: new ObjectId(userId) })
    return c.body(null, 204)
  } catch (e) {
    return handleError(c, 'me:delete', e)
  }
})
