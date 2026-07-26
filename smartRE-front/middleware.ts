import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

const PROTECTED_PREFIXES = ['/dashboard', '/listings', '/properties/new', '/verification', '/ownership', '/viewings', '/payments', '/reviews', '/profile', '/agent-application', '/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', '/agent-applications']
const ADMIN_PREFIXES = ['/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', '/agent-applications']

function decode(token: string): { exp?: number; role?: string } | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

export function middleware(req: NextRequest) {
  const path = req.nextUrl.pathname
  const isProtected = PROTECTED_PREFIXES.some(p => path === p || path.startsWith(p + '/'))
  if (!isProtected) return NextResponse.next()

  const token = req.cookies.get('sre_token')?.value
  const claims = token ? decode(token) : null
  const expired = !claims || !claims.exp || Date.now() >= claims.exp * 1000

  if (!token || expired) {
    const url = req.nextUrl.clone()
    url.pathname = '/login'
    url.searchParams.set('returnTo', path)
    return NextResponse.redirect(url)
  }

  const isAdminRoute = ADMIN_PREFIXES.some(p => path === p || path.startsWith(p + '/'))
  if (isAdminRoute && claims?.role !== 'ADMIN') {
    const url = req.nextUrl.clone()
    url.pathname = '/'
    return NextResponse.redirect(url)
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/dashboard/:path*', '/listings/:path*', '/properties/new', '/verification/:path*', '/ownership/:path*', '/viewings/:path*', '/payments/:path*', '/reviews/:path*', '/profile/:path*', '/agent-application/:path*', '/overview/:path*', '/revenue/:path*', '/users/:path*', '/verification-queue/:path*', '/manage-listings/:path*', '/reports/:path*', '/agent-applications/:path*'],
}
