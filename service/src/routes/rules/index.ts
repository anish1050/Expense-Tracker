import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { MongoClientFindOne } from '#helpers/mongoclient.js'
import { notFoundResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { ParserRulesDoc } from '#types/docs.js'

const querySchema = z.object({ since_version: z.coerce.number().int().nonnegative().default(0) }).strict()

export const rulesRoutes = new Hono()

rulesRoutes.get('/', zValidator('query', querySchema, validationHook), async (c) => {
  const { since_version: sinceVersion } = c.req.valid('query')
  try {
    const latest = await MongoClientFindOne<ParserRulesDoc>('parser_rules', {}, { sort: { version: -1 }, projection: { version: 1, rules: 1 } })
    // A missing ruleset is a real 404; database errors now throw and become 500.
    if (!latest.data) return notFoundResponse(c, 'No ruleset published')
    if (latest.data.version <= sinceVersion) return c.body(null, 304)
    c.header('Cache-Control', 'public, max-age=3600')
    return successResponse(c, { version: latest.data.version, rules: latest.data.rules })
  } catch (e) {
    return handleError(c, 'rules:get', e)
  }
})
