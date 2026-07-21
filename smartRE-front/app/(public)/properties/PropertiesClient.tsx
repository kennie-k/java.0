'use client'
import { useEffect, useState, useCallback } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Search, MapPin, SlidersHorizontal, Building2, X } from 'lucide-react'
import { propertyApi } from '@/lib/api'
import { useDebounce } from '@/hooks/useDebounce'
import type { PropertyResponse } from '@/types'
import PropertyCard from '@/components/property/PropertyCard'
import RevealCard from '@/components/ui/RevealCard'
import Input from '@/components/ui/Input'
import Button from '@/components/ui/Button'
import { EmptyState, SkeletonCard } from '@/components/ui/Modal'

const PAGE_SIZE = 12

export default function PropertiesClient() {
  const router = useRouter()
  const sp = useSearchParams()

  const [items, setItems] = useState<PropertyResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)

  const [keywordInput, setKeywordInput] = useState(sp.get('keyword') || '')
  const [countyInput, setCountyInput] = useState(sp.get('county') || '')
  const [propertyType, setPropertyType] = useState(sp.get('propertyType') || '')
  const [listingType, setListingType] = useState(sp.get('listingType') || '')
  const [minBedrooms, setMinBedrooms] = useState(sp.get('minBedrooms') || '')
  const [minPriceInput, setMinPriceInput] = useState(sp.get('minPrice') || '')
  const [maxPriceInput, setMaxPriceInput] = useState(sp.get('maxPrice') || '')

  const keyword = useDebounce(keywordInput)
  const county = useDebounce(countyInput)
  const minPrice = useDebounce(minPriceInput)
  const maxPrice = useDebounce(maxPriceInput)

  const load = useCallback((p: number) => {
    setLoading(true)
    propertyApi.search({
      keyword: keyword || undefined,
      county: county || undefined,
      propertyType: propertyType || undefined,
      listingType: listingType || undefined,
      minBedrooms: minBedrooms || undefined,
      minPrice: minPrice || undefined,
      maxPrice: maxPrice || undefined,
      page: p,
      size: PAGE_SIZE,
    }).then(r => { setItems(r.content || []); setTotal(r.totalElements || 0) })
      .catch(() => { setItems([]); setTotal(0) })
      .finally(() => setLoading(false))
  }, [keyword, county, propertyType, listingType, minBedrooms, minPrice, maxPrice])

  useEffect(() => {
    setPage(0)
    load(0)
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (county) params.set('county', county)
    if (propertyType) params.set('propertyType', propertyType)
    if (listingType) params.set('listingType', listingType)
    if (minBedrooms) params.set('minBedrooms', minBedrooms)
    if (minPrice) params.set('minPrice', minPrice)
    if (maxPrice) params.set('maxPrice', maxPrice)
    router.replace(`/properties${params.toString() ? `?${params.toString()}` : ''}`, { scroll: false })
  }, [keyword, county, propertyType, listingType, minBedrooms, minPrice, maxPrice]) // eslint-disable-line react-hooks/exhaustive-deps

  const clearAll = () => {
    setKeywordInput(''); setCountyInput(''); setPropertyType(''); setListingType('')
    setMinBedrooms(''); setMinPriceInput(''); setMaxPriceInput('')
  }

  const activeCount = [keyword, county, propertyType, listingType, minBedrooms, minPrice, maxPrice].filter(Boolean).length

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <div className="mb-4">
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">
          {county ? `Property in ${county}` : 'Browse property listings'}
        </h1>
        <p className="text-[12px] text-muted mt-0.5">{total.toLocaleString()} verified listings{keyword ? ` matching "${keyword}"` : ''}</p>
      </div>

      <div className="card p-2.5 flex flex-col sm:flex-row gap-2 mb-3.5">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
          <input
            value={keywordInput}
            onChange={e => setKeywordInput(e.target.value)}
            placeholder="Search title, estate, description..."
            className="w-full h-8 pl-8 pr-3 rounded-md bg-gray-50 dark:bg-white/5 text-[13px] placeholder:text-gray-400 focus:outline-none"/>
        </div>
        <Button variant="secondary" size="sm" leftIcon={<SlidersHorizontal size={12}/>} onClick={() => setShowFilters(s => !s)}>
          Filters {activeCount > 0 && <span className="ml-0.5 badge bg-gold-500 text-white">{activeCount}</span>}
        </Button>
      </div>

      {showFilters && (
        <div className="card p-3.5 mb-4 animate-fade-in space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
            <Input placeholder="County" leftIcon={<MapPin size={13}/>} value={countyInput} onChange={e => setCountyInput(e.target.value)}/>
            <select value={propertyType} onChange={e => setPropertyType(e.target.value)} className="input-base cursor-pointer">
              <option value="">All types</option>
              <option value="HOUSE">House</option><option value="APARTMENT">Apartment</option>
              <option value="LAND">Land</option><option value="COMMERCIAL">Commercial</option><option value="VILLA">Villa</option>
            </select>
            <select value={listingType} onChange={e => setListingType(e.target.value)} className="input-base cursor-pointer">
              <option value="">Sale or rent</option>
              <option value="SALE">For sale</option><option value="RENT">For rent</option>
            </select>
            <select value={minBedrooms} onChange={e => setMinBedrooms(e.target.value)} className="input-base cursor-pointer">
              <option value="">Any bedrooms</option>
              <option value="1">1+</option><option value="2">2+</option><option value="3">3+</option><option value="4">4+</option>
            </select>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 items-end">
            <Input label="Min price (KES)" type="number" placeholder="0" value={minPriceInput} onChange={e => setMinPriceInput(e.target.value)}/>
            <Input label="Max price (KES)" type="number" placeholder="Any" value={maxPriceInput} onChange={e => setMaxPriceInput(e.target.value)}/>
            <Button variant="ghost" size="sm" leftIcon={<X size={12}/>} onClick={clearAll}>Clear all</Button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array(9).fill(0).map((_, i) => <SkeletonCard key={i}/>)}
        </div>
      ) : items.length === 0 ? (
        <EmptyState icon={<Building2 size={24}/>} title="No properties match those filters"
          desc="Try widening your search or clearing a filter."
          action={<Button variant="secondary" size="sm" onClick={clearAll}>Clear filters</Button>}/>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {items.map((p, i) => <RevealCard key={p.id} index={i}><PropertyCard property={p} priority={i < 3}/></RevealCard>)}
        </div>
      )}

      {total > PAGE_SIZE && (
        <div className="flex items-center justify-center gap-2 mt-6">
          <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => { const np = page - 1; setPage(np); load(np) }}>Previous</Button>
          <span className="text-[12px] text-muted px-2">Page {page + 1} of {Math.max(1, Math.ceil(total / PAGE_SIZE))}</span>
          <Button variant="secondary" size="sm" disabled={(page + 1) * PAGE_SIZE >= total} onClick={() => { const np = page + 1; setPage(np); load(np) }}>Next</Button>
        </div>
      )}
    </div>
  )
}
