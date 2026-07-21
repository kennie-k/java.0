'use client'
import { useEffect, useState, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Building2, Calendar, CreditCard, ShieldCheck, ArrowRight, TrendingUp, Eye } from 'lucide-react'
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { useAuthStore } from '@/lib/store'
import { propertyApi, viewingApi, paymentApi } from '@/lib/api'
import { StatCard, Card } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { SkeletonCard } from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import { fmt } from '@/lib/utils'

export default function DashboardPage() {
  const { user } = useAuthStore()
  const router = useRouter()
  const [data, setData] = useState<any>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (user?.role === 'ADMIN') { router.replace('/overview'); return }
    if (!user) return
    const load = async () => {
      try {
        const [props, viewings, payments] = await Promise.allSettled([
          user.role === 'SELLER' ? propertyApi.my() : propertyApi.search({ size: 6, sort: 'createdAt,desc' }),
          user.role === 'BUYER' ? viewingApi.myBuyer() : viewingApi.mySeller(),
          paymentApi.my(),
        ])
        setData({
          properties: props.status === 'fulfilled' ? props.value : null,
          viewings:   viewings.status === 'fulfilled' ? (viewings.value.content || []) : [],
          payments:   payments.status === 'fulfilled' ? (payments.value.content || []) : [],
        })
      } finally { setLoading(false) }
    }
    load()
  }, [user, router])

  const props  = useMemo(() => data.properties?.content || data.properties || [], [data.properties])
  const views  = useMemo(() => data.viewings || [], [data.viewings])
  const pays   = useMemo(() => data.payments || [], [data.payments])
  const activeProps = props.filter((p:any) => p.status === 'ACTIVE').length
  const pendViews   = views.filter((v:any) => ['PENDING_FEE','REQUESTED','CONFIRMED'].includes(v.status)).length
  const completePays= pays.filter((p:any) => p.status === 'COMPLETED').length

  const activityTrend = useMemo(() => {
    const weeks: { name: string; start: Date }[] = []
    const now = new Date()
    for (let i = 7; i >= 0; i--) {
      const start = new Date(now)
      start.setDate(now.getDate() - i * 7)
      weeks.push({ name: start.toLocaleDateString('en-KE', { month: 'short', day: 'numeric' }), start })
    }
    return weeks.map((w, i) => {
      const end = i < weeks.length - 1 ? weeks[i + 1].start : new Date(now.getTime() + 86400000)
      const inRange = (d: string) => { const t = new Date(d).getTime(); return t >= w.start.getTime() && t < end.getTime() }
      return {
        name: w.name,
        viewings: views.filter((v: any) => v.scheduledAt && inRange(v.scheduledAt)).length,
        payments: pays.filter((p: any) => p.createdAt && inRange(p.createdAt)).length,
      }
    })
  }, [views, pays])

  const topListings = useMemo(() => {
    return [...props]
      .sort((a: any, b: any) => (b.viewCount || 0) - (a.viewCount || 0))
      .slice(0, 5)
      .map((p: any) => ({ name: p.title.length > 18 ? p.title.slice(0, 18) + '…' : p.title, views: p.viewCount || 0 }))
  }, [props])

  if (!user || user.role === 'ADMIN') return null

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">
          Good {new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 17 ? 'afternoon' : 'evening'}, {user.fullName.split(' ')[0]}
        </h1>
        <p className="text-muted text-[12px] mt-0.5">Here&apos;s what&apos;s happening with your account today.</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {loading ? Array(4).fill(0).map((_,i)=><SkeletonCard key={i}/>) : <>
          <StatCard label={user.role === 'SELLER' ? 'My listings' : 'Listings live now'}
            value={props.length} icon={<Building2 size={17}/>} color="gold"/>
          <StatCard label="Active listings" value={activeProps} icon={<TrendingUp size={17}/>} color="emerald"/>
          <StatCard label="Viewings" value={pendViews} sub="pending confirmation" icon={<Calendar size={17}/>} color="blue"/>
          <StatCard label="Payments" value={completePays} sub="completed" icon={<CreditCard size={17}/>} color="purple"/>
        </>}
      </div>

      {user.role === 'SELLER' && !user.verified && (
        <div className="rounded-lg border border-amber-200 dark:border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 p-3.5 flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-amber-100 dark:bg-amber-500/20 text-amber-600 flex items-center justify-center shrink-0">
            <ShieldCheck size={16}/>
          </div>
          <div className="flex-1">
            <p className="font-semibold text-[13px] text-amber-800 dark:text-amber-300">Complete your identity verification</p>
            <p className="text-[12px] text-amber-700 dark:text-amber-400 mt-0.5">Verify your identity to activate your listings and build buyer trust.</p>
          </div>
          <Link href="/verification">
            <Button size="sm" variant="secondary">Start verification <ArrowRight size={13}/></Button>
          </Link>
        </div>
      )}

      <div className={`grid gap-4 ${user.role === 'SELLER' ? 'lg:grid-cols-3' : 'lg:grid-cols-2'}`}>
        <Card className={user.role === 'SELLER' ? 'lg:col-span-2' : ''}>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px]">Activity, last 8 weeks</h2>
            <span className="text-[11px] text-muted">Viewings &amp; payments</span>
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
                <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
                <Area type="monotone" dataKey="viewings" name="Viewings" stroke="#3B82F6" strokeWidth={2} fill="url(#viewingsFill)"/>
                <Area type="monotone" dataKey="payments" name="Payments" stroke="#C9A227" strokeWidth={2} fill="url(#paymentsFill)"/>
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>

        {user.role === 'SELLER' && (
          <Card>
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display font-semibold text-[13px] flex items-center gap-1"><Eye size={13} className="text-gold-500"/>Top listings</h2>
              <span className="text-[11px] text-muted">By views</span>
            </div>
            {loading ? <div className="skeleton h-[180px] rounded-md"/> : topListings.length === 0 ? (
              <div className="h-[180px] flex items-center justify-center text-[12px] text-muted">No listings yet</div>
            ) : (
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={topListings} layout="vertical" margin={{ left: 0, right: 12 }}>
                  <XAxis type="number" hide allowDecimals={false}/>
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false} width={100}/>
                  <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
                  <Bar dataKey="views" fill="#C9A227" radius={[0, 4, 4, 0]} barSize={14}/>
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        )}
      </div>

      <div className="grid lg:grid-cols-2 gap-4">
        <div className="card p-4">
          <div className="flex items-center justify-between mb-3.5">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white">{user.role === 'SELLER' ? 'My listings' : 'Newest listings'}</h2>
            <Link href={user.role === 'SELLER' ? '/listings' : '/properties'} className="text-[12px] text-gold-500 hover:text-gold-600 font-medium flex items-center gap-1">View all <ArrowRight size={12}/></Link>
          </div>
          {loading ? <div className="space-y-2.5">{[0,1,2].map(i=><div key={i} className="skeleton h-12 rounded-md"/>)}</div>
          : props.length === 0 ? (
            <div className="text-center py-6">
              <Building2 size={26} className="mx-auto text-gray-300 dark:text-gray-700 mb-2"/>
              <p className="text-[12px] text-muted">No properties yet</p>
              {user.role === 'SELLER' && <Link href="/properties/new" className="mt-2.5 inline-block"><Button size="sm">Add property</Button></Link>}
            </div>
          ) : (
            <div className="space-y-2">
              {props.slice(0,4).map((p: any) => (
                <Link key={p.id} href={`/properties/${p.id}`} className="flex items-center gap-2.5 p-2 rounded-md hover:bg-gray-50 dark:hover:bg-[#2E2518] transition-colors">
                  <div className="w-9 h-9 rounded-md bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                    <Building2 size={14}/>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[12px] font-medium text-gray-900 dark:text-white truncate">{p.title}</p>
                    <p className="text-[11px] text-muted">{p.county} · {fmt.currency(p.price)}</p>
                  </div>
                  <StatusBadge status={p.status} size="sm"/>
                </Link>
              ))}
            </div>
          )}
        </div>

        <div className="card p-4">
          <div className="flex items-center justify-between mb-3.5">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white">Recent viewings</h2>
            <Link href="/viewings" className="text-[12px] text-gold-500 hover:text-gold-600 font-medium flex items-center gap-1">View all <ArrowRight size={12}/></Link>
          </div>
          {loading ? <div className="space-y-2.5">{[0,1,2].map(i=><div key={i} className="skeleton h-12 rounded-md"/>)}</div>
          : views.length === 0 ? (
            <div className="text-center py-6">
              <Calendar size={26} className="mx-auto text-gray-300 dark:text-gray-700 mb-2"/>
              <p className="text-[12px] text-muted">No viewings scheduled</p>
            </div>
          ) : (
            <div className="space-y-2">
              {views.slice(0,4).map((v: any) => (
                <div key={v.id} className="flex items-center gap-2.5 p-2 rounded-md bg-gray-50 dark:bg-[#2E2518]">
                  <div className="w-9 h-9 rounded-md bg-blue-100 dark:bg-blue-500/10 text-blue-500 flex items-center justify-center shrink-0">
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
        </div>
      </div>
    </div>
  )
}
