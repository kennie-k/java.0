/** @type {import('next').NextConfig} */
const { URL } = require('url')

// next/image's optimizer fetches remote URLs *server-side* on our behalf, so
// remotePatterns is effectively an allowlist of hosts our server is willing
// to make outbound requests to. `hostname: '**'` turned that into an open
// proxy (SSRF/DoS risk: anyone could ask our server to fetch+resize an
// arbitrary URL). Only the hosts we actually serve property/document photos
// from need to be listed: the API gateway (local-disk storage fallback in
// dev, configurable via NEXT_PUBLIC_API_URL) and the S3 bucket used for
// production uploads (see smartRE/.env S3_PUBLIC_BASE_URL / NEXT_PUBLIC_S3_PUBLIC_URL).
function hostPattern(urlString, fallback) {
  try {
    const u = new URL(urlString)
    return {
      protocol: u.protocol.replace(':', ''),
      hostname: u.hostname,
      ...(u.port ? { port: u.port } : {}),
    }
  } catch {
    return fallback
  }
}

const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
const assetHost = process.env.NEXT_PUBLIC_S3_PUBLIC_URL || 'https://smartre-documents.s3.amazonaws.com'

const remotePatterns = [
  hostPattern(apiUrl, { protocol: 'http', hostname: 'localhost' }),
  hostPattern(assetHost, { protocol: 'https', hostname: 'smartre-documents.s3.amazonaws.com' }),
]

const nextConfig = {
  images: {
    remotePatterns,
    dangerouslyAllowSVG: true,
    contentDispositionType: 'attachment',
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(self)' },
          { key: 'Content-Security-Policy', value: "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://maps.googleapis.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https: http://localhost:8080; connect-src 'self' https://maps.googleapis.com http://localhost:8080 https:; frame-src 'self' https://www.google.com;" },
        ],
      },
    ]
  },
}
module.exports = nextConfig
