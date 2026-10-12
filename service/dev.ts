import { serve } from '@hono/node-server'
import app from './src/index.js'

const port = Number(process.env.PORT ?? 3002)
serve({ fetch: app.fetch, port }, () => console.log(`listening on http://localhost:${port}`))
