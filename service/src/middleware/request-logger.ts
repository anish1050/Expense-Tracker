import type { MiddlewareHandler } from 'hono'
import { MongoClientInsertOne } from '#helpers/mongoclient.js'
import type { RequestLogDoc } from '#types/docs.js'
import type { AuthVariables } from './proxy.js'

// Fire-and-forget request log into a TTL collection. Never logs bodies.
export const requestLoggerMiddleware: MiddlewareHandler<{ Variables: AuthVariables }> = async (c, next) => {
  const started = Date.now()
  await next()
  if (process.env.NODE_ENV === 'test') return
  void MongoClientInsertOne<RequestLogDoc>('request_logs', {
    method: c.req.method,
    path: c.req.path,
    status: c.res.status,
    duration_ms: Date.now() - started,
    user_id: c.get('userId'),
    created_at: new Date(),
  })
}
