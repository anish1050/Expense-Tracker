import { Hono } from 'hono'
import { cors } from 'hono/cors'
import { logger } from 'hono/logger'
import { trimTrailingSlash } from 'hono/trailing-slash'
import { ensureIndexes } from '#helpers/indexes.js'
import { proxyMiddleware, type AuthVariables } from '#middleware/proxy.js'
import { authMiddleware } from '#middleware/auth.js'
import { rateLimit } from '#middleware/rate-limit.js'
import { requestLoggerMiddleware } from '#middleware/request-logger.js'
import { googleAuthRoutes } from '#routes/auth/google.js'
import { syncRoutes } from '#routes/sync/index.js'
import { rulesRoutes } from '#routes/rules/index.js'
import { meRoutes } from '#routes/me/index.js'

const app = new Hono<{ Variables: AuthVariables }>()

app.use('*', cors({ origin: '*', allowHeaders: ['Content-Type', 'Authorization'], allowMethods: ['GET', 'POST', 'DELETE', 'OPTIONS'] }))
app.use(trimTrailingSlash())
if (process.env.NODE_ENV !== 'test') app.use('*', logger())
app.use('*', async (_c, next) => {
  await ensureIndexes()
  await next()
})
app.use('*', proxyMiddleware)
app.use('*', requestLoggerMiddleware)

app.use('/api/sync/*', authMiddleware)
app.use('/api/me', authMiddleware)

app.use('/api/auth/*', rateLimit(10, 60, 'auth'))
app.use('/api/sync/*', rateLimit(60, 60, 'sync'))
app.use('/api/me', rateLimit(10, 60, 'me'))

app.get('/api/health', (c) => c.json({ status: 'ok', timestamp: new Date().toISOString() }))

app.onError((err, c) => {
  console.error('[unhandled]', err.message)
  return c.json({ success: false, error: 'Internal Server Error' }, 500)
})
app.notFound((c) => c.json({ success: false, error: 'Not Found' }, 404))

app.route('/api/auth', googleAuthRoutes)
app.route('/api/sync', syncRoutes)
app.route('/api/rules', rulesRoutes)
app.route('/api/me', meRoutes)

export default app
