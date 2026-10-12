import type { MiddlewareHandler } from 'hono'
import { unauthorizedResponse } from '#helpers/response.js'
import type { AuthVariables } from './proxy.js'

export const authMiddleware: MiddlewareHandler<{ Variables: AuthVariables }> = async (c, next) => {
  if (!c.get('userId')) return unauthorizedResponse(c)
  await next()
}
