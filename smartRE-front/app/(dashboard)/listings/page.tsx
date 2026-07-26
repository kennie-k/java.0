'use client'
import { useMemo } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { Building2, Plus, MapPin, Eye } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { useMyListings } from '@/hooks/useMyListings'
import { SELLER_ROLES } from '@/lib/roles'
import Button from '@/components/ui/Button'
import { Card, StatCard } from '@/components/ui/Card'
import { StatusBadge, TrustBadge } from '@/components/ui/Badge'
import { EmptyState, SkeletonCard, PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'

export default function MyListingsPage() {
  const { ready } = useAuthGuard(SELLER_ROLES, '/dashboard')
  const { items, loading, active, inReview, closed, totalViews, hasMore, loadMore, loadingMore } = useMyListings()

  const viewsChart = useMemo(() => {
    return [...items]
      .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
      .slice(0, 6)
      .map(p => ({ name: p.title.length > 16 ? p.title.slice(0, 16) + '…' : p.title, views: p.viewCount || 0 }))
  }, [items])

  if (!ready) return <PageLoader/>

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">My listings</h1>
          <p className="text-muted text-sm mt-0.5">{items.length} propert{items.length === 1 ? 'y' : 'ies'} listed</p>
        </div>
        <Link href="/properties/new"><Button leftIcon={<Plus size={16}/>}>Add property</Button></Link>
      </div>

      {!loading && items.length > 0 && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label="Active" value={active} color="emerald"/>
          <StatCard label="In review" value={inReview} color="gold"/>
          <StatCard label="Sold / rented" value={closed} color="blue"/>
          <StatCard label="Total views" value={totalViews} icon={<Eye size={18}/>} color="purple"/>
        </div>
      )}

      {!loading && items.length > 0 && (
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px] flex items-center gap-1"><Eye size={13} className="text-gold-500"/>Views by listing</h2>
            <span className="text-[11px] text-muted">Top {Math.min(6, items.length)}</span>
          </div>
          <ResponsiveContainer width="100%" height={Math.max(120, viewsChart.length * 34)}>
            <BarChart data={viewsChart} layout="vertical" margin={{ left: 0, right: 12 }}>
              <XAxis type="number" hide allowDecimals={false}/>
              <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} width={120}/>
              <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
              <Bar dataKey="views" fill="#C9A227" radius={[0, 4, 4, 0]} barSize={16}/>
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array(3).fill(0).map((_, i) => <SkeletonCard key={i}/>)}
        </div>
      ) : items.length === 0 ? (
        <EmptyState icon={<Building2 size={26}/>} title="No listings yet"
          desc="Add your first property. It stays in draft until your identity and land title are verified."
          action={<Link href="/properties/new"><Button>List a property</Button></Link>}/>
      ) : (
        <Card padding="none">
          <div className="divide-y divide-gray-100 dark:divide-[#1E1E3A]">
            {items.map(p => (
              <Link key={p.id} href={`/properties/${p.id}`} className="flex items-center gap-3 p-3.5 hover:bg-gray-50 dark:hover:bg-[#1A1A35] transition-colors">
                <div className="w-14 h-14 rounded-lg bg-gradient-to-br from-gold-100 to-amber-50 dark:from-gold-500/10 dark:to-amber-500/5 flex items-center justify-center shrink-0 overflow-hidden relative">
                  {p.imageUrls?.[0] ? (
                    <Image src={p.imageUrls[0]} alt={p.title} fill sizes="56px" className="object-cover"/>
                  ) : <Building2 size={20} className="text-gold-300"/>}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[13px] font-semibold text-gray-900 dark:text-white truncate">{p.title}</p>
                  <p className="text-[11px] text-muted flex items-center gap-1"><MapPin size={10}/>{p.county} · {fmt.currency(p.price)}</p>
                </div>
                <div className="hidden sm:flex items-center gap-1 text-[11px] text-muted"><Eye size={11}/>{p.viewCount}</div>
                <TrustBadge identityVerified={p.sellerIdentityVerified} ownershipVerified={p.propertyOwnershipVerified} fullyTrusted={p.fullyTrusted} size="sm"/>
                <StatusBadge status={p.status} size="sm"/>
              </Link>
            ))}
          </div>
        </Card>
      )}

      {hasMore && (
        <div className="flex justify-center">
          <Button variant="secondary" size="sm" loading={loadingMore} onClick={() => loadMore()}>Load more</Button>
        </div>
      )}
    </div>
  )
}
