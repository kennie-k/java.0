import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'
import { jwtVerify } from 'jose'

// ---------------------------------------------------------------------------
// SECURITY NOTE — read this before assuming middleware.ts is an auth boundary
//
// This file is a routing convenience, not the platform's authorization
// boundary. Its only job is to bounce an obviously-unauthenticated or
// wrong-role browser away from a route *before* React mounts, so nobody sees
// a flash of a page they can't use. It must never be the sole gate in front
// of real data.
//
// Every page under (dashboard) and (admin) fetches its data through
// lib/api.ts, which sends the httpOnly `sre_token` cookie straight to the
// backend services, and those services re-verify the JWT signature
// themselves (see e.g. user-service/src/main/java/.../security/JwtUtil.java,
// which signs/verifies with the shared JWT_SECRET). That backend check is
// the actual security boundary. Do not add code here — or in any page — that
// renders or exposes admin/sensitive data based solely on the role decoded
// below; always require a corresponding authenticated backend call to have
// succeeded first.
//
// Signature verification: if JWT_SECRET is configured for this frontend
// deployment (the same HMAC key the backend signs with — see smartRE/.env),
// we verify the token's signature at the edge with `jose` (lightweight,
// edge-runtime compatible) before trusting its claims for the redirect
// decisions below. If JWT_SECRET is NOT set, we fall back to an *unverified*
// base64 decode purely to decide whether to show a redirect — in that mode,
// `claims` is attacker-controllable (anyone can hand-craft a cookie value
// with `role: "ADMIN"` in an unsigned-looking payload) and must never be
// treated as proof of identity or role. The `verified` flag on the result
// tells you which mode produced a given claims object.
// ---------------------------------------------------------------------------

export const PROTECTED_PREFIXES = ['/dashboard', '/listings', '/properties/new', '/verification', '/ownership', '/viewings', '/payments', '/reviews', '/profile', '/agent-application', '/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', '/agent-applications']
export const ADMIN_PREFIXES = ['/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', '/agent-applications']

export interface AuthClaims {
  exp?: number
  role?: string
}

export interface ResolvedAuth {
  claims: AuthClaims | null
  /** true iff `claims` came from a verified HMAC signature check, not a raw decode. */
  verified: boolean
}

// UNTRUSTED — decodes the JWT payload without checking its signature. Only
// ever use this for non-security UX decisions (e.g. "should we redirect to
// /login") and never to authorize access to data. See file header.
export function decodeUnverified(token: string): AuthClaims | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

export function secretFromEnv(): Uint8Array | null {
  const raw = process.env.JWT_SECRET
  return raw ? new TextEncoder().encode(raw) : null
}

// Resolves a token to claims, verifying the signature with `jose` when a
// shared secret is available. `secret` is injectable for tests; production
// code should call this with no second argument (defaults to env).
export async function resolveAuth(
  token: string | undefined,
  secret: Uint8Array | null = secretFromEnv()
): Promise<ResolvedAuth> {
  if (!token) return { claims: null, verified: !!secret }

  if (secret) {
    try {
      const { payload } = await jwtVerify(token, secret)
      return { claims: payload as AuthClaims, verified: true }
    } catch {
      // Invalid signature, malformed token, or expired (jose checks `exp`
      // itself) — treat identically to "no session".
      return { claims: null, verified: true }
    }
  }

  return { claims: decodeUnverified(token), verified: false }
}

export function isExpired(claims: AuthClaims | null): boolean {
  return !claims || !claims.exp || Date.now() >= claims.exp * 1000
}

function matchesPrefix(path: string, prefixes: string[]): boolean {
  return prefixes.some(p => path === p || path.startsWith(p + '/'))
}

export async function middleware(req: NextRequest) {
  const path = req.nextUrl.pathname
  if (!matchesPrefix(path, PROTECTED_PREFIXES)) return NextResponse.next()

  const token = req.cookies.get('sre_token')?.value
  const { claims } = await resolveAuth(token)

  if (!token || isExpired(claims)) {
    const url = req.nextUrl.clone()
    url.pathname = '/login'
    url.searchParams.set('returnTo', path)
    return NextResponse.redirect(url)
  }

  // NOTE: this is still a UX redirect only, even in verified mode — see file
  // header. The backend independently re-checks role on every admin request.
  if (matchesPrefix(path, ADMIN_PREFIXES) && claims?.role !== 'ADMIN') {
    const url = req.nextUrl.clone()
    url.pathname = '/'
    return NextResponse.redirect(url)
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/dashboard/:path*', '/listings/:path*', '/properties/new', '/verification/:path*', '/ownership/:path*', '/viewings/:path*', '/payments/:path*', '/reviews/:path*', '/profile/:path*', '/agent-application/:path*', '/overview/:path*', '/revenue/:path*', '/users/:path*', '/verification-queue/:path*', '/manage-listings/:path*', '/reports/:path*', '/agent-applications/:path*'],
}
