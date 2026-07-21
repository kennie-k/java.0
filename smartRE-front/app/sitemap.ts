import type { MetadataRoute } from 'next'

const BASE = 'https://smartre.co.ke'
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

async function fetchPropertyIds(): Promise<{ id: string; updatedAt: string }[]> {
  try {
    const res = await fetch(`${API_BASE}/api/properties/search?size=500`, { next: { revalidate: 3600 } })
    if (!res.ok) return []
    const body = await res.json()
    return (body.content || []).map((p: any) => ({ id: p.id, updatedAt: p.updatedAt }))
  } catch {
    return []
  }
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticRoutes = ['', '/properties', '/login', '/register'].map(path => ({
    url: `${BASE}${path}`,
    lastModified: new Date(),
    changeFrequency: path === '' || path === '/properties' ? 'hourly' as const : 'monthly' as const,
    priority: path === '' ? 1 : path === '/properties' ? 0.9 : 0.5,
  }))

  const properties = await fetchPropertyIds()
  const propertyRoutes = properties.map(p => ({
    url: `${BASE}/properties/${p.id}`,
    lastModified: new Date(p.updatedAt),
    changeFrequency: 'daily' as const,
    priority: 0.8,
  }))

  return [...staticRoutes, ...propertyRoutes]
}
