'use client'
import { useEffect, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Search, SlidersHorizontal, Building2, X, ShieldCheck } from 'lucide-react'
import { propertyApi } from '@/lib/api'
import { useDebounce } from '@/hooks/useDebounce'
import { queryKeys } from '@/lib/queryKeys'
import { KENYA_COUNTIES } from '@/lib/counties'
import PropertyCard from '@/components/property/PropertyCard'
import RevealCard from '@/components/ui/RevealCard'
import Input from '@/components/ui/Input'
import Button from '@/components/ui/Button'
import { EmptyState, SkeletonCard } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'

const PAGE_SIZE = 12

export default function PropertiesClient() {
  const router = useRouter()
  const sp = useSearchParams()

  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)

  const [keywordInput, setKeywordInput] = useState(sp.get('keyword') || '')
  const [countyInput, setCountyInput] = useState(sp.get('county') || '')
  const [propertyType, setPropertyType] = useState(sp.get('propertyType') || '')
  const [listingType, setListingType] = useState(sp.get('listingType') || '')
  const [minBedrooms, setMinBedrooms] = useState(sp.get('minBedrooms') || '')
  const [minPriceInput, setMinPriceInput] = useState(sp.get('minPrice') || '')
  const [maxPriceInput, setMaxPriceInput] = useState(sp.get('maxPrice') || '')
  const [verifiedOnly, setVerifiedOnly] = useState(sp.get('verifiedOnly') === 'true')

  const keyword = useDebounce(keywordInput)
  const county = useDebounce(countyInput)
  const minPrice = useDebounce(minPriceInput)
  const maxPrice = useDebounce(maxPriceInput)

  const filters = {
    keyword: keyword || undefined,
    county: county || undefined,
    propertyType: propertyType || undefined,
    listingType: listingType || undefined,
    minBedrooms: minBedrooms || undefined,
    minPrice: minPrice || undefined,
    maxPrice: maxPrice || undefined,
    verifiedOnly: verifiedOnly || undefined,
  }

  const { data, isLoading: loading, isError } = useQuery({
    queryKey: queryKeys.propertySearch({ ...filters, page, size: PAGE_SIZE }),
    queryFn: () => propertyApi.search({ ...filters, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
  const items = data?.content ?? []
  const total = data?.totalElements ?? 0

  useEffect(() => {
    setPage(0)
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (county) params.set('county', county)
    if (propertyType) params.set('propertyType', propertyType)
    if (listingType) params.set('listingType', listingType)
    if (minBedrooms) params.set('minBedrooms', minBedrooms)
    if (minPrice) params.set('minPrice', minPrice)
    if (maxPrice) params.set('maxPrice', maxPrice)
    if (verifiedOnly) params.set('verifiedOnly', 'true')
    router.replace(`/properties${params.toString() ? `?${params.toString()}` : ''}`, { scroll: false })
  }, [keyword, county, propertyType, listingType, minBedrooms, minPrice, maxPrice, verifiedOnly])

  const clearAll = () => {
    setKeywordInput(''); setCountyInput(''); setPropertyType(''); setListingType('')
    setMinBedrooms(''); setMinPriceInput(''); setMaxPriceInput(''); setVerifiedOnly(false)
  }

  const activeCount = [keyword, county, propertyType, listingType, minBedrooms, minPrice, maxPrice, verifiedOnly || undefined].filter(Boolean).length

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <div className="mb-4">
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">
          {county ? `Property in ${county}` : 'Browse property listings'}
        </h1>
        <p className="text-[12px] text-muted mt-0.5">{total.toLocaleString()} verified listings{keyword ? ` matching "${keyword}"` : ''}</p>
      </div>

      {isError && <InlineError message="Failed to load listings. Try adjusting your filters."/>}

      <div className="card p-2.5 flex flex-col sm:flex-row gap-2 mb-3.5">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
          <input
            value={keywordInput}
            onChange={e => setKeywordInput(e.target.value)}
            placeholder="Search title, estate, description..."
            className="w-full h-8 pl-8 pr-3 rounded-md bg-gray-50 dark:bg-white/5 border border-transparent text-[13px] placeholder:text-gray-400 transition-all focus:outline-none focus:ring-2 focus:ring-gold-500/20 focus:border-gold-500 focus:bg-white dark:focus:bg-white/10"/>
        </div>
        <Button variant="secondary" size="sm" leftIcon={<SlidersHorizontal size={12}/>} onClick={() => setShowFilters(s => !s)}>
          Filters {activeCount > 0 && <span className="ml-0.5 badge bg-gold-500 text-white">{activeCount}</span>}
        </Button>
      </div>

      {showFilters && (
        <div className="card p-3.5 mb-4 animate-fade-in space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
            <select value={countyInput} onChange={e => setCountyInput(e.target.value)} className="input-base cursor-pointer">
              <option value="">All counties</option>
              {KENYA_COUNTIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
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
          <label className="flex items-center gap-2 text-[12px] p-2 rounded-md border border-base cursor-pointer w-fit">
            <input type="checkbox" checked={verifiedOnly} onChange={e => setVerifiedOnly(e.target.checked)}/>
            <ShieldCheck size={13} className="text-emerald-600"/>Fully verified listings only
          </label>
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
          <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</Button>
          <span className="text-[12px] text-muted px-2">Page {page + 1} of {Math.max(1, Math.ceil(total / PAGE_SIZE))}</span>
          <Button variant="secondary" size="sm" disabled={(page + 1) * PAGE_SIZE >= total} onClick={() => setPage(p => p + 1)}>Next</Button>
        </div>
      )}
    </div>
  )
}
