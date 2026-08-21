'use client'
import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useQueries } from '@tanstack/react-query'
import { Building2, Calendar, CreditCard, ShieldCheck, ArrowRight, TrendingUp, Eye, Activity, PieChart as PieChartIcon } from 'lucide-react'
import { AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { useAuthStore } from '@/lib/store'
import { isSellerOrAgent } from '@/lib/roles'
import { propertyApi, viewingApi, paymentApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { StatCard, Card } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { SkeletonCard } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import RevealCard from '@/components/ui/RevealCard'
import Button from '@/components/ui/Button'
import { fmt } from '@/lib/utils'

const EMPTY: never[] = []
const VIEWING_STATUS_COLORS: Record<string, string> = {
  PENDING_FEE: '#F59E0B', REQUESTED: '#3B82F6', CONFIRMED: '#8B5CF6',
  COMPLETED: '#10B981', CANCELLED: '#9CA3AF', NO_SHOW: '#EF4444',
}
const VIEWING_STATUS_LABELS: Record<string, string> = {
  PENDING_FEE: 'Pending fee', REQUESTED: 'Requested', CONFIRMED: 'Confirmed',
  COMPLETED: 'Completed', CANCELLED: 'Cancelled', NO_SHOW: 'No-show',
}

export default function DashboardPage() {
  const { user } = useAuthStore()
  const router = useRouter()
  const isSeller = isSellerOrAgent(user)
  const enabled = !!user && user.role !== 'ADMIN'
  const base = queryKeys.dashboard(user?.role ?? 'GUEST')
  const [weeks, setWeeks] = useState(8)

  useEffect(() => {
    if (user?.role === 'ADMIN') router.replace('/overview')
  }, [user, router])

  const [propertiesQ, viewingsQ, paymentsQ] = useQueries({
    queries: [
      {
        queryKey: [...base, 'properties'],
        queryFn: () => (isSeller ? propertyApi.my(0, 100) : propertyApi.search({ size: 6, sort: 'createdAt,desc' })),
        enabled,
      },
      {
        queryKey: [...base, 'viewings'],
        queryFn: () => (user?.role === 'BUYER' ? viewingApi.myBuyer(0, 100) : viewingApi.mySeller(0, 100)),
        enabled,
      },
      {
        queryKey: [...base, 'payments'],
        queryFn: () => paymentApi.my(0, 100),
        enabled,
      },
    ],
  })

  const loading = propertiesQ.isLoading || viewingsQ.isLoading || paymentsQ.isLoading
  const loadError = propertiesQ.isError || viewingsQ.isError || paymentsQ.isError

  const props = propertiesQ.data?.content ?? EMPTY
  const views = viewingsQ.data?.content ?? EMPTY
  const pays = paymentsQ.data?.content ?? EMPTY
  const activeProps = props.filter(p => p.status === 'ACTIVE').length
  const pendViews = views.filter(v => ['PENDING_FEE', 'REQUESTED', 'CONFIRMED'].includes(v.status)).length
  const completePays = pays.filter(p => p.status === 'COMPLETED').length

  const activityTrend = useMemo(() => {
    const buckets: { name: string; start: Date }[] = []
    const now = new Date()
    for (let i = weeks - 1; i >= 0; i--) {
      const start = new Date(now)
      start.setDate(now.getDate() - i * 7)
      buckets.push({ name: start.toLocaleDateString('en-KE', { month: 'short', day: 'numeric' }), start })
    }
    return buckets.map((w, i) => {
      const end = i < buckets.length - 1 ? buckets[i + 1].start : new Date(now.getTime() + 86400000)
      const inRange = (d: string) => { const t = new Date(d).getTime(); return t >= w.start.getTime() && t < end.getTime() }
      return {
        name: w.name,
        viewings: views.filter(v => v.scheduledAt && inRange(v.scheduledAt)).length,
        payments: pays.filter(p => p.createdAt && inRange(p.createdAt)).length,
      }
    })
  }, [views, pays, weeks])

  const topListings = useMemo(() => {
    return [...props]
      .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
      .slice(0, 5)
      .map(p => ({ name: p.title.length > 18 ? p.title.slice(0, 18) + '…' : p.title, views: p.viewCount || 0 }))
  }, [props])

  const viewingStatusBreakdown = useMemo(() => {
    const counts: Record<string, number> = {}
    for (const v of views) counts[v.status] = (counts[v.status] || 0) + 1
    return Object.entries(counts).map(([status, count]) => ({ status, name: VIEWING_STATUS_LABELS[status] || status, value: count }))
  }, [views])

  if (!user || user.role === 'ADMIN') return null

  const today = new Date().toLocaleDateString('en-KE', { weekday: 'long', month: 'long', day: 'numeric' })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">
            Welcome, {user.fullName.split(' ')[0]}
          </h1>
          <p className="text-muted text-[12.5px] mt-1">{today}</p>
        </div>
      </div>

      {loadError && <InlineError message="Some dashboard data failed to load."/>}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {loading ? Array(4).fill(0).map((_,i)=><SkeletonCard key={i}/>) : [
          <StatCard key="listings" label={isSeller ? 'My listings' : 'Listings shown'}
            value={props.length} sub={isSeller ? 'across all statuses' : 'most recent matches'} icon={<Building2 size={16}/>} color="gold"/>,
          <StatCard key="active" label="Active listings" value={activeProps}
            sub={isSeller && props.length > 0 ? `${Math.round((activeProps / props.length) * 100)}% of your listings` : 'currently live'} icon={<TrendingUp size={16}/>} color="emerald"/>,
          <StatCard key="viewings" label="Viewings" value={views.length} sub={`${pendViews} pending confirmation`} icon={<Calendar size={16}/>} color="blue"/>,
          <StatCard key="payments" label="Payments" value={pays.length} sub={`${completePays} completed`} icon={<CreditCard size={16}/>} color="purple"/>,
        ].map((card, i) => <RevealCard key={card.key} index={i}>{card}</RevealCard>)}
      </div>

      {isSeller && !user.verified && (
        <div className="rounded-xl border border-amber-200 dark:border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 px-4 py-3.5 flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-white dark:bg-amber-500/15 text-amber-600 flex items-center justify-center shrink-0 shadow-sm">
            <ShieldCheck size={16}/>
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-[13px] text-amber-900 dark:text-amber-200">Complete your identity verification</p>
            <p className="text-[12px] text-amber-700 dark:text-amber-400/90 mt-0.5">Verify your identity to activate your listings and build buyer trust.</p>
          </div>
          <Link href="/verification" className="shrink-0">
            <Button size="sm" variant="secondary">Start verification <ArrowRight size={13}/></Button>
          </Link>
        </div>
      )}

      <div className={`grid gap-4 ${isSeller ? 'lg:grid-cols-3' : 'lg:grid-cols-2'}`}>
        <Card className={isSeller ? 'lg:col-span-2' : ''}>
          <div className="flex items-center justify-between mb-4 gap-2">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2">
              <Activity size={14} className="text-muted"/>Activity
            </h2>
            <select value={weeks} onChange={e => setWeeks(Number(e.target.value))} aria-label="Activity period"
              className="text-[11px] font-medium text-muted bg-transparent border border-base rounded-md pl-2 pr-6 py-1 hover:border-gold-300 dark:hover:border-gold-500/40 focus:outline-none focus:ring-2 focus:ring-gold-500/20 cursor-pointer transition-colors">
              <option value={4}>Last 4 weeks</option>
              <option value={8}>Last 8 weeks</option>
              <option value={12}>Last 12 weeks</option>
            </select>
          </div>
          <div className="flex items-center gap-3 text-[10.5px] text-muted mb-3">
            <span className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-blue-500"/>Viewings</span>
            <span className="flex items-center gap-1.5"><span className="w-1.5 h-1.5 rounded-full bg-gold-500"/>Payments</span>
          </div>
          {loading ? <div className="skeleton h-[180px] rounded-md"/> : (
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={activityTrend}>
                <defs>
                  <linearGradient id="viewingsFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3B82F6" stopOpacity={0.3}/>
                    <stop offset="100%" stopColor="#3B82F6" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="paymentsFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#C9A227" stopOpacity={0.35}/>
                    <stop offset="100%" stopColor="#C9A227" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.15}/>
                <XAxis dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false}/>
                <YAxis tick={{ fontSize: 10 }} axisLine={false} tickLine={false} width={24} allowDecimals={false}/>
                <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                <Area type="monotone" dataKey="viewings" name="Viewings" stroke="#3B82F6" strokeWidth={2} fill="url(#viewingsFill)"/>
                <Area type="monotone" dataKey="payments" name="Payments" stroke="#C9A227" strokeWidth={2} fill="url(#paymentsFill)"/>
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>

        {isSeller ? (
          <Card>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2"><Eye size={14} className="text-muted"/>Top listings</h2>
              <span className="text-[10.5px] text-muted bg-gray-50 dark:bg-white/5 px-2 py-0.5 rounded-full">By views</span>
            </div>
            {loading ? <div className="skeleton h-[180px] rounded-md"/> : topListings.length === 0 ? (
              <div className="h-[180px] flex items-center justify-center text-[12px] text-muted">No listings yet</div>
            ) : (
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={topListings} layout="vertical" margin={{ left: 0, right: 12 }}>
                  <XAxis type="number" hide allowDecimals={false}/>
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false} width={100}/>
                  <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                  <Bar dataKey="views" fill="#C9A227" radius={[0, 4, 4, 0]} barSize={14}/>
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        ) : (
          <Card>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2"><PieChartIcon size={14} className="text-muted"/>Viewing status</h2>
              <span className="text-[10.5px] text-muted bg-gray-50 dark:bg-white/5 px-2 py-0.5 rounded-full">All time</span>
            </div>
            {loading ? <div className="skeleton h-[180px] rounded-md"/> : viewingStatusBreakdown.length === 0 ? (
              <div className="h-[180px] flex items-center justify-center text-[12px] text-muted">No viewings yet</div>
            ) : (
              <>
                <ResponsiveContainer width="100%" height={140}>
                  <PieChart>
                    <Pie data={viewingStatusBreakdown} dataKey="value" nameKey="name" innerRadius={38} outerRadius={58} paddingAngle={3}>
                      {viewingStatusBreakdown.map(d => <Cell key={d.status} fill={VIEWING_STATUS_COLORS[d.status] || '#9CA3AF'}/>)}
                    </Pie>
                    <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                  </PieChart>
                </ResponsiveContainer>
                <div className="flex flex-wrap justify-center gap-x-3 gap-y-1 mt-1 text-[10.5px] text-muted">
                  {viewingStatusBreakdown.map(d => (
                    <span key={d.status} className="flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: VIEWING_STATUS_COLORS[d.status] || '#9CA3AF' }}/>{d.name} ({d.value})
                    </span>
                  ))}
                </div>
              </>
            )}
          </Card>
        )}
      </div>

      <div className="grid lg:grid-cols-2 gap-4">
        <Card className="card-hover">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2"><Building2 size={14} className="text-muted"/>{isSeller ? 'My listings' : 'Newest listings'}</h2>
            <Link href={isSeller ? '/listings' : '/properties'} className="group text-[12px] text-gold-500 hover:text-gold-600 font-medium flex items-center gap-1">
              View all <ArrowRight size={12} className="transition-transform duration-150 group-hover:translate-x-0.5"/>
            </Link>
          </div>
          {loading ? <div className="space-y-2.5">{[0,1,2].map(i=><div key={i} className="skeleton h-12 rounded-md"/>)}</div>
          : props.length === 0 ? (
            <div className="text-center py-6">
              <Building2 size={26} className="mx-auto text-gray-300 dark:text-gray-700 mb-2"/>
              <p className="text-[12px] text-muted">No properties yet</p>
              {isSeller && <Link href="/properties/new" className="mt-2.5 inline-block"><Button size="sm">Add property</Button></Link>}
            </div>
          ) : (
            <div className="space-y-1">
              {props.slice(0,4).map(p => (
                <Link key={p.id} href={`/properties/${p.id}`} className="group flex items-center gap-2.5 p-2 -mx-2 rounded-md hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                  <div className="w-9 h-9 rounded-md bg-gray-50 dark:bg-white/5 text-gold-500 flex items-center justify-center shrink-0 transition-transform duration-150 group-hover:scale-105">
                    <Building2 size={14}/>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[12px] font-medium text-gray-900 dark:text-white truncate">{p.title}</p>
                    <p className="text-[11px] text-muted">{p.county} · {fmt.currency(p.price)}</p>
                  </div>
                  <StatusBadge status={p.status} size="sm"/>
                  <ArrowRight size={13} className="text-gray-300 dark:text-gray-700 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-150 shrink-0"/>
                </Link>
              ))}
            </div>
          )}
        </Card>

        <Card className="card-hover">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2"><Calendar size={14} className="text-muted"/>Recent viewings</h2>
            <Link href="/viewings" className="group text-[12px] text-gold-500 hover:text-gold-600 font-medium flex items-center gap-1">
              View all <ArrowRight size={12} className="transition-transform duration-150 group-hover:translate-x-0.5"/>
            </Link>
          </div>
          {loading ? <div className="space-y-2.5">{[0,1,2].map(i=><div key={i} className="skeleton h-12 rounded-md"/>)}</div>
          : views.length === 0 ? (
            <div className="text-center py-6">
              <Calendar size={26} className="mx-auto text-gray-300 dark:text-gray-700 mb-2"/>
              <p className="text-[12px] text-muted">No viewings scheduled</p>
            </div>
          ) : (
            <div className="space-y-1">
              {views.slice(0,4).map(v => (
                <div key={v.id} className="flex items-center gap-2.5 p-2 -mx-2 rounded-md">
                  <div className="w-9 h-9 rounded-md bg-gray-50 dark:bg-white/5 text-blue-500 flex items-center justify-center shrink-0">
                    <Calendar size={14}/>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[12px] font-medium text-gray-900 dark:text-white">{fmt.date(v.scheduledAt)}</p>
                    <p className="text-[11px] text-muted">{fmt.datetime(v.scheduledAt)}</p>
                  </div>
                  <StatusBadge status={v.status} size="sm"/>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
