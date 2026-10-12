import { SignJWT, jwtVerify, createRemoteJWKSet } from 'jose'
import { requireEnv } from './env.js'

const GOOGLE_JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'))
const GOOGLE_ISSUERS = ['https://accounts.google.com', 'accounts.google.com']

const appSecret = () => new TextEncoder().encode(requireEnv('JWT_SECRET'))

export interface GoogleIdentity {
  sub: string
  email: string
  name: string
}

export async function verifyGoogleIdToken(idToken: string): Promise<GoogleIdentity> {
  const { payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
    audience: requireEnv('GOOGLE_CLIENT_ID'),
    issuer: GOOGLE_ISSUERS,
  })
  if (!payload.sub) throw new Error('Google token has no subject')
  return { sub: payload.sub, email: String(payload.email ?? ''), name: String(payload.name ?? '') }
}

export function signAppJwt(userId: string): Promise<string> {
  return new SignJWT({})
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime('30d')
    .sign(appSecret())
}

export async function verifyAppJwt(token: string): Promise<string | null> {
  try {
    const { payload } = await jwtVerify(token, appSecret(), { algorithms: ['HS256'] })
    return payload.sub ?? null
  } catch {
    return null
  }
}
