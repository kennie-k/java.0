'use client'
import Link from 'next/link'
import Image from 'next/image'
import { Building2, MapPin, Bed, Bath, Ruler, Eye } from 'lucide-react'
import type { PropertyResponse } from '@/types'
import { StatusBadge, TrustBadge } from '@/components/ui/Badge'
import { fmt } from '@/lib/utils'

export default function PropertyCard({ property: p, priority }:{ property: PropertyResponse; priority?: boolean }) {
  return (
    <Link href={`/properties/${p.id}`} className="card-hover block group overflow-hidden">
      <div className="h-40 bg-gradient-to-br from-gold-100 to-amber-50 dark:from-gold-500/10 dark:to-amber-500/5 rounded-t-xl flex items-center justify-center relative overflow-hidden">
        {p.imageUrls?.[0] ? (
          <Image src={p.imageUrls[0]} alt={p.title} fill priority={priority} sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw"
            className="object-cover group-hover:scale-105 transition-transform duration-300"/>
        ) : <Building2 size={32} className="text-gold-300"/>}
        <div className="absolute top-2.5 left-2.5 flex gap-1.5">
          <span className="badge bg-white/95 text-gray-700 dark:bg-black/60 dark:text-white text-[10px] font-semibold">{p.listingType === 'SALE' ? 'For Sale' : 'To Rent'}</span>
          <span className="badge bg-white/95 text-gray-700 dark:bg-black/60 dark:text-white text-[10px]">{p.propertyType}</span>
        </div>
        <div className="absolute top-2.5 right-2.5"><StatusBadge status={p.status} size="sm"/></div>
        {p.price > 0 && (
          <div className="absolute bottom-2.5 left-2.5">
            <span className="font-display font-bold text-white text-sm drop-shadow-md bg-black/40 backdrop-blur-sm px-2 py-0.5 rounded-md">
              {fmt.currency(p.price)}{p.listingType === 'RENT' && <span className="text-[10px] font-normal">/mo</span>}
            </span>
          </div>
        )}
      </div>
      <div className="p-3.5">
        <h3 className="font-display font-semibold text-gray-900 dark:text-white text-[13px] leading-snug line-clamp-1 mb-0.5">{p.title}</h3>
        <p className="text-[11px] text-muted flex items-center gap-1 mb-2.5"><MapPin size={10}/>{p.city ? `${p.city}, ` : ''}{p.county}</p>
        <div className="flex items-center gap-2.5 text-[11px] text-muted mb-2.5">
          {!!p.bedrooms && <span className="flex items-center gap-1"><Bed size={11}/>{p.bedrooms}</span>}
          {!!p.bathrooms && <span className="flex items-center gap-1"><Bath size={11}/>{p.bathrooms}</span>}
          {!!p.areaSqm && <span className="flex items-center gap-1"><Ruler size={11}/>{p.areaSqm}m²</span>}
          <span className="flex items-center gap-1 ml-auto"><Eye size={10}/>{p.viewCount}</span>
        </div>
        <div className="flex items-center justify-between pt-2 border-t border-base">
          <TrustBadge identityVerified={p.sellerIdentityVerified} ownershipVerified={p.propertyOwnershipVerified} fullyTrusted={p.fullyTrusted} size="sm"/>
        </div>
      </div>
    </Link>
  )
}
