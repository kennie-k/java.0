import { describe, it, expect } from 'vitest'
import { SignJWT } from 'jose'
import { NextRequest } from 'next/server'
import {
  decodeUnverified, resolveAuth, isExpired, middleware,
  PROTECTED_PREFIXES, ADMIN_PREFIXES,
} from './middleware'

function base64url(obj: object) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url')
}

// An "unsigned" token shaped like a real JWT but with a garbage signature —
// exactly what decodeUnverified() will happily decode, and exactly what a
// verified resolveAuth() must reject.
function fakeToken(payload: object) {
  const header = base64url({ alg: 'HS256', typ: 'JWT' })
  const body = base64url(payload)
  return `${header}.${body}.not-a-real-signature`
}

describe('decodeUnverified (untrusted decode — see middleware.ts file header)', () => {
  it('decodes claims out of a well-formed payload', () => {
    const token = fakeToken({ role: 'ADMIN', exp: 9999999999 })
    expect(decodeUnverified(token)?.role).toBe('ADMIN')
  })

  it('decodes a forged token just as happily — this is exactly why it must never be used for authorization', () => {
    // Nothing about this token is genuine; only the signature would normally catch that.
    const forged = fakeToken({ role: 'ADMIN', exp: 9999999999 })
    expect(decodeUnverified(forged)).toEqual({ role: 'ADMIN', exp: 9999999999 })
  })

  it('returns null for malformed input instead of throwing', () => {
    expect(decodeUnverified('not-a-jwt')).toBeNull()
    expect(decodeUnverified('')).toBeNull()
  })
})

describe('resolveAuth', () => {
  const secret = new TextEncoder().encode('test-shared-secret-at-least-32-bytes-long!!')

  it('with no secret configured: falls back to an unverified decode and flags verified:false', async () => {
    const token = fakeToken({ role: 'ADMIN', exp: 9999999999 })
    const result = await resolveAuth(token, null)
    expect(result.verified).toBe(false)
    expect(result.claims?.role).toBe('ADMIN')
  })

  it('with a secret configured: rejects a forged token instead of trusting its claims', async () => {
    const forged = fakeToken({ role: 'ADMIN', exp: 9999999999 })
    const result = await resolveAuth(forged, secret)
    expect(result.verified).toBe(true)
    expect(result.claims).toBeNull()
  })

  it('with a secret configured: accepts a token genuinely signed with that secret', async () => {
    const token = await new SignJWT({ role: 'ADMIN' })
      .setProtectedHeader({ alg: 'HS256' })
      .setExpirationTime('1h')
      .sign(secret)
    const result = await resolveAuth(token, secret)
    expect(result.verified).toBe(true)
    expect(result.claims?.role).toBe('ADMIN')
  })

  it('treats a signature-valid but expired token as no session', async () => {
    const token = await new SignJWT({ role: 'ADMIN' })
      .setProtectedHeader({ alg: 'HS256' })
      .setExpirationTime(Math.floor(Date.now() / 1000) - 60)
      .sign(secret)
    const result = await resolveAuth(token, secret)
    expect(result.claims).toBeNull()
  })

  it('returns no claims when there is no token at all', async () => {
    expect((await resolveAuth(undefined, secret)).claims).toBeNull()
    expect((await resolveAuth(undefined, null)).claims).toBeNull()
  })
})

describe('isExpired', () => {
  it('treats null claims, a missing exp, and a past exp as expired', () => {
    expect(isExpired(null)).toBe(true)
    expect(isExpired({})).toBe(true)
    expect(isExpired({ exp: Math.floor(Date.now() / 1000) - 3600 })).toBe(true)
  })

  it('treats a future exp as not expired', () => {
    expect(isExpired({ exp: Math.floor(Date.now() / 1000) + 3600 })).toBe(false)
  })
})

describe('route prefix config', () => {
  it('every admin-only prefix is also gated as a protected prefix', () => {
    for (const p of ADMIN_PREFIXES) expect(PROTECTED_PREFIXES).toContain(p)
  })
})

// These run in "unverified" mode since no JWT_SECRET is set in the test env —
// which is itself the default/most-common deployment mode this file supports,
// so it's worth covering the full middleware() handler under it.
describe('middleware()', () => {
  it('redirects to /login when there is no cookie on a protected route', async () => {
    const req = new NextRequest(new URL('http://localhost/dashboard'))
    const res = await middleware(req)
    expect(res.status).toBe(307)
    expect(res.headers.get('location')).toContain('/login')
  })

  it('passes through untouched on a route that is not protected', async () => {
    const req = new NextRequest(new URL('http://localhost/properties/abc123'))
    const res = await middleware(req)
    expect(res.status).toBe(200)
  })

  it('redirects a non-admin-role token away from an admin route', async () => {
    const token = fakeToken({ role: 'BUYER', exp: Math.floor(Date.now() / 1000) + 3600 })
    const req = new NextRequest(new URL('http://localhost/users'), { headers: { cookie: `sre_token=${token}` } })
    const res = await middleware(req)
    expect(res.status).toBe(307)
    expect(res.headers.get('location')).not.toContain('/login')
  })

  it('lets an admin-role token through to an admin route', async () => {
    const token = fakeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 3600 })
    const req = new NextRequest(new URL('http://localhost/users'), { headers: { cookie: `sre_token=${token}` } })
    const res = await middleware(req)
    expect(res.status).toBe(200)
  })

  it('redirects an expired token to /login', async () => {
    const token = fakeToken({ role: 'ADMIN', exp: Math.floor(Date.now() / 1000) - 3600 })
    const req = new NextRequest(new URL('http://localhost/dashboard'), { headers: { cookie: `sre_token=${token}` } })
    const res = await middleware(req)
    expect(res.status).toBe(307)
    expect(res.headers.get('location')).toContain('/login')
  })
})
