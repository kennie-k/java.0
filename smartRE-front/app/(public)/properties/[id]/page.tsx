import type { Metadata } from 'next'
import PropertyDetailClient from './PropertyDetailClient'

const BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

async function fetchProperty(id: string) {
  try {
    const res = await fetch(`${BASE}/api/properties/${id}`, { next: { revalidate: 300 } })
    if (!res.ok) return null
    return res.json()
  } catch { return null }
}

export async function generateMetadata({ params }: { params: { id: string } }): Promise<Metadata> {
  const p = await fetchProperty(params.id)
  if (!p) return { title: 'Property Listing' }
  const title = `${p.title} · ${p.county} | SmartRE Kenya`
  const description = `${p.propertyType} for ${p.listingType === 'RENT' ? 'rent' : 'sale'} in ${p.city || p.county}, Kenya. ${p.bedrooms ? `${p.bedrooms} bed, ` : ''}${p.bathrooms ? `${p.bathrooms} bath. ` : ''}Identity and title verification: ${p.fullyTrusted ? 'fully verified' : p.sellerIdentityVerified ? 'seller identity verified' : 'in progress'}.`
  return {
    title, description,
    alternates: { canonical: `/properties/${params.id}` },
    openGraph: { title, description, type: 'website', images: p.imageUrls?.[0] ? [p.imageUrls[0]] : undefined },
  }
}

export default function PropertyDetailPage() {
  return <PropertyDetailClient/>
}
