import type { MetadataRoute } from 'next'

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      { userAgent: '*', allow: '/', disallow: ['/dashboard', '/listings', '/verification', '/viewings', '/payments', '/reviews', '/profile', '/overview', '/revenue', '/users', '/verification-queue'] },
    ],
    sitemap: 'https://smartre.co.ke/sitemap.xml',
  }
}
