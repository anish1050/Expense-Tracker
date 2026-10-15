import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { MongoClientFindOneAndUpdate } from '#helpers/mongoclient.js'
import { GoogleTokenError, signAppJwt, verifyGoogleIdToken } from '#helpers/jwt.js'
import { successResponse, unauthorizedResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { CoreUserDoc } from '#types/docs.js'

const bodySchema = z.object({ id_token: z.string().min(20) }).strict()

export const googleAuthRoutes = new Hono()

googleAuthRoutes.post('/google', zValidator('json', bodySchema, validationHook), async (c) => {
  const { id_token: idToken } = c.req.valid('json')

  let identity
  try {
    identity = await verifyGoogleIdToken(idToken)
  } catch (e) {
    // Only a token the user controls is a 401; our own config or Google's JWKS
    // being unreachable is a 500 so the misconfiguration is not hidden.
    if (e instanceof GoogleTokenError) return unauthorizedResponse(c, 'Invalid Google token')
    return handleError(c, 'auth:google', e)
  }

  try {
    const now = new Date()
    const user = await MongoClientFindOneAndUpdate<CoreUserDoc>(
      'core_users',
      { google_sub: identity.sub },
      {
        $setOnInsert: { google_sub: identity.sub, created_at: now, sync_seq: 0 },
        $set: { email: identity.email, name: identity.name, last_login_at: now },
      },
      { upsert: true, returnDocument: 'after' },
    )
    if (!user.data) return handleError(c, 'auth:google', user.message)

    const userId = user.data._id.toString()
    const accessToken = await signAppJwt(userId)
    return successResponse(c, { access_token: accessToken, user: { id: userId, email: user.data.email, name: user.data.name } })
  } catch (e) {
    return handleError(c, 'auth:google', e)
  }
})
