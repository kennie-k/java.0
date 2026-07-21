'use client'
import Link from 'next/link'
import {
  DollarSign, TrendingUp, TrendingDown, Building2, ShieldCheck, ShieldAlert,
  Search, ArrowUpRight, Landmark, Smartphone, Database, Server, Activity,
  CheckCircle2, Clock, Star, ListChecks, BarChart3, Zap,
} from 'lucide-react'
import { useAdminOverview } from '@/hooks/useAdminOverview'
import { Card, StatCard } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { PageLoader } from '@/components/ui/Modal'
import { fmt, cn } from '@/lib/utils'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts'

const PIE_COLORS = ['#C9A227', '#3B82F6', '#10B981', '#8B5CF6', '#F59E0B']

const services = [
  { name: 'API Gateway', port: 8080, note: '3→20 replicas · rate limit 50/s' },
  { name: 'User Service', port: 8081, note: 'Auth · JWT issuance' },
  { name: 'Verification', port: 8082, note: 'Identity + ownership pipelines' },
  { name: 'Property Service', port: 8083, note: 'Listings · search cache' },
  { name: 'Viewing Service', port: 8084, note: 'Scheduling · fee gating' },
  { name: 'Payment Service', port: 8085, note: 'M-Pesa STK · escrow' },
  { name: 'Review Service', port: 8086, note: 'Post-payment ratings' },
]

export default function AdminOverview() {
  const { loading, summary, revenue, users, properties, idQueue, ownQueue, stats, query, setQuery, searchResults, growth } = useAdminOverview()

  if (loading) return <PageLoader/>

  return (
    <div className="space-y-5 pb-10">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Command Center</h1>
          <p className="text-[13px] text-muted mt-0.5">Full platform overview · {users.length} users · {properties.length} listings · {fmt.currency(summary?.totalPlatformFees || 0)} lifetime revenue</p>
        </div>
        <div className="relative w-full sm:w-72">
          <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
          <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search users, properties, counties..."
            className="w-full h-9 pl-8 pr-3 rounded-lg bg-gray-50 dark:bg-white/5 text-[12px] placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-gold-500/20 border border-base"/>
          {searchResults && (searchResults.users.length > 0 || searchResults.properties.length > 0) && (
            <div className="absolute right-0 top-[calc(100%+6px)] w-80 card z-50 py-1 max-h-80 overflow-auto animate-fade-in">
              {searchResults.users.length > 0 && (
                <div className="px-3 pt-2 pb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-400">Users</div>
              )}
              {searchResults.users.map(u => (
                <Link key={u.id} href="/users" className="flex items-center gap-2 px-3 py-2 text-[12px] hover:bg-gray-50 dark:hover:bg-white/5">
                  <div className="w-6 h-6 rounded-full bg-gold-500 text-white text-[9px] font-bold flex items-center justify-center shrink-0">{fmt.initials(u.fullName)}</div>
                  <span className="truncate">{u.fullName}</span><span className="text-muted ml-auto shrink-0">{u.role}</span>
                </Link>
              ))}
              {searchResults.properties.length > 0 && (
                <div className="px-3 pt-2 pb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-400 border-t border-base mt-1">Properties</div>
              )}
              {searchResults.properties.map(p => (
                <Link key={p.id} href={`/properties/${p.id}`} className="flex items-center gap-2 px-3 py-2 text-[12px] hover:bg-gray-50 dark:hover:bg-white/5">
                  <Building2 size={12} className="text-gold-400 shrink-0"/>
                  <span className="truncate">{p.title}</span><span className="text-muted ml-auto shrink-0">{p.county}</span>
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <StatCard label="Lifetime revenue" value={fmt.currency(summary?.totalPlatformFees || 0)}
          sub={<span className={cn('flex items-center gap-0.5', growth >= 0 ? 'text-emerald-500' : 'text-red-500')}>{growth >= 0 ? <TrendingUp size={11}/> : <TrendingDown size={11}/>}{Math.abs(growth)}% vs last month</span>}
          icon={<DollarSign size={17}/>} color="gold"/>
        <StatCard label="Active listings" value={stats.active} sub={`${stats.pending} pending · ${stats.draft} draft`} icon={<Building2 size={17}/>} color="blue"/>
        <StatCard label="Verified users" value={`${stats.verifiedPct}%`} sub={`${stats.buyers} buyers · ${stats.sellers} sellers`} icon={<ShieldCheck size={17}/>} color="emerald"/>
        <StatCard label="Fraud strikes" value={stats.fraudStrikes} sub={`${stats.banned} permanently banned`} icon={<ShieldAlert size={17}/>} color="purple"/>
        <StatCard label="Escrow pending" value={stats.pendingPayout} sub={`${fmt.currency(stats.totalPayout)} in payouts`} icon={<Clock size={17}/>} color="gold"/>
      </div>

      <div className="grid lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px]">Revenue trend</h2>
            <span className="text-[11px] text-muted">Platform fees by month</span>
          </div>
          {stats.trendChart.length === 0 ? (
            <div className="h-[180px] flex flex-col items-center justify-center text-center">
              <TrendingUp size={22} className="text-gray-300 dark:text-gray-700 mb-1.5"/>
              <p className="text-[12px] text-muted">No revenue recorded yet</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <AreaChart data={stats.trendChart}>
                <defs>
                  <linearGradient id="rev" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#C9A227" stopOpacity={0.35}/>
                    <stop offset="100%" stopColor="#C9A227" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <XAxis dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false}/>
                <YAxis tick={{ fontSize: 10 }} axisLine={false} tickLine={false} width={40}/>
                <Tooltip formatter={(v: any) => [fmt.currency(v), 'Revenue']} contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
                <Area type="monotone" dataKey="value" stroke="#C9A227" strokeWidth={2} fill="url(#rev)"/>
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card>
          <h2 className="font-display font-semibold text-[13px] mb-3">Revenue split</h2>
          {stats.commission === 0 && stats.viewingFee === 0 ? (
            <div className="h-[140px] flex flex-col items-center justify-center text-center">
              <DollarSign size={22} className="text-gray-300 dark:text-gray-700 mb-1.5"/>
              <p className="text-[12px] text-muted">No revenue yet</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={140}>
              <PieChart>
                <Pie data={[{ name: 'Commission', value: stats.commission }, { name: 'Viewing fees', value: stats.viewingFee }]}
                  dataKey="value" innerRadius={38} outerRadius={58} paddingAngle={3}>
                  <Cell fill="#C9A227"/><Cell fill="#3B82F6"/>
                </Pie>
                <Tooltip formatter={(v: any) => fmt.currency(v)} contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
              </PieChart>
            </ResponsiveContainer>
          )}
          <div className="flex items-center justify-center gap-4 text-[11px] mt-1">
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-gold-500"/>Commission</span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"/>Viewing fees</span>
          </div>
        </Card>
      </div>

      <div className="grid lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <h2 className="font-display font-semibold text-[13px] mb-3">Listings by property type</h2>
          {stats.typeChart.length === 0 ? (
            <div className="h-[160px] flex flex-col items-center justify-center text-center">
              <Building2 size={22} className="text-gray-300 dark:text-gray-700 mb-1.5"/>
              <p className="text-[12px] text-muted">No listings yet</p>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={stats.typeChart}>
                <XAxis dataKey="name" tick={{ fontSize: 10 }} axisLine={false} tickLine={false}/>
                <YAxis tick={{ fontSize: 10 }} axisLine={false} tickLine={false} width={26}/>
                <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12 }}/>
                <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                  {stats.typeChart.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]}/>)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
          <div className="flex flex-wrap gap-3 mt-2 text-[11px] text-muted">
            <span>Avg price: <b className="text-gray-900 dark:text-white">{fmt.currency(stats.avgPrice)}</b></span>
            <span>Total views: <b className="text-gray-900 dark:text-white">{stats.totalViews.toLocaleString()}</b></span>
            <span>Fully trusted: <b className="text-gray-900 dark:text-white">{stats.fullyTrusted}</b></span>
            <span>Closed deals: <b className="text-gray-900 dark:text-white">{stats.closed}</b></span>
          </div>
        </Card>

        <Card>
          <h2 className="font-display font-semibold text-[13px] mb-3">Top counties</h2>
          <div className="space-y-2.5">
            {stats.topCounties.map(([county, count]) => (
              <div key={county} className="flex items-center gap-2">
                <span className="text-[12px] text-gray-700 dark:text-gray-300 w-24 truncate">{county}</span>
                <div className="flex-1 h-1.5 rounded-full bg-gray-100 dark:bg-white/5 overflow-hidden">
                  <div className="h-full bg-gold-500 rounded-full" style={{ width: `${(count / (stats.topCounties[0]?.[1] || 1)) * 100}%` }}/>
                </div>
                <span className="text-[11px] text-muted w-6 text-right">{count}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <div className="grid lg:grid-cols-3 gap-4">
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px] flex items-center gap-1.5"><ListChecks size={14} className="text-gold-500"/>Identity queue</h2>
            <Link href="/verification-queue" className="text-[11px] text-gold-500 flex items-center gap-0.5">All <ArrowUpRight size={11}/></Link>
          </div>
          <p className="font-display text-lg font-semibold text-gray-900 dark:text-white mb-1">{idQueue.length}</p>
          <p className="text-[11px] text-muted">awaiting human review</p>
        </Card>
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px] flex items-center gap-1.5"><Landmark size={14} className="text-gold-500"/>Ownership queue</h2>
            <Link href="/verification-queue" className="text-[11px] text-gold-500 flex items-center gap-0.5">All <ArrowUpRight size={11}/></Link>
          </div>
          <p className="font-display text-lg font-semibold text-gray-900 dark:text-white mb-1">{ownQueue.length}</p>
          <p className="text-[11px] text-muted">titles pending Ardhisasa / legal review</p>
        </Card>
        <Card>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display font-semibold text-[13px] flex items-center gap-1.5"><Star size={14} className="text-gold-500"/>Top sellers by payout</h2>
          </div>
          <div className="space-y-2">
            {stats.topSellers.length === 0 && <p className="text-[11px] text-muted">No payouts yet</p>}
            {stats.topSellers.map(([id, amt], i) => (
              <div key={id} className="flex items-center gap-2 text-[12px]">
                <span className="w-4 text-muted">{i + 1}</span>
                <span className="flex-1 truncate font-mono text-[11px] text-gray-600 dark:text-gray-300">{id.slice(0, 8)}…</span>
                <span className="font-semibold text-gray-900 dark:text-white">{fmt.currency(amt)}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card>
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-display font-semibold text-[13px] flex items-center gap-1.5"><Server size={14} className="text-gold-500"/>Platform architecture</h2>
          <span className="text-[11px] text-muted flex items-center gap-1"><Activity size={11} className="text-emerald-500"/>7 microservices · Kafka · Redis · PgBouncer · K8s HPA</span>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2.5">
          {services.map(s => (
            <div key={s.port} className="rounded-lg border border-base p-2.5 bg-gray-50/60 dark:bg-white/[0.02]">
              <div className="flex items-center gap-1.5 mb-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0"/>
                <span className="text-[11px] font-semibold text-gray-900 dark:text-white truncate">{s.name}</span>
              </div>
              <p className="text-[10px] text-muted">:{s.port}</p>
              <p className="text-[10px] text-muted leading-tight mt-0.5">{s.note}</p>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-x-5 gap-y-1.5 mt-4 pt-3 border-t border-base text-[11px] text-muted">
          <span className="flex items-center gap-1"><Database size={11}/>6 Postgres DBs via PgBouncer</span>
          <span className="flex items-center gap-1"><Zap size={11}/>5 Kafka topics · DLQ w/ 3 retries</span>
          <span className="flex items-center gap-1"><Smartphone size={11}/>M-Pesa Daraja STK + B2C</span>
          <span className="flex items-center gap-1"><CheckCircle2 size={11}/>2 circuit breakers · Resilience4j</span>
          <span className="flex items-center gap-1"><BarChart3 size={11}/>Prometheus + Grafana + Loki</span>
        </div>
      </Card>

      <Card padding="none">
        <div className="flex items-center justify-between p-4 pb-0">
          <h2 className="font-display font-semibold text-[13px]">Recent transactions</h2>
          <Link href="/revenue" className="text-[11px] text-gold-500 flex items-center gap-0.5">Full ledger <ArrowUpRight size={11}/></Link>
        </div>
        <div className="divide-y divide-gray-100 dark:divide-[#1E1E3A] mt-3">
          {revenue.slice(0, 8).map(r => (
            <div key={r.id} className="flex items-center gap-3 px-4 py-2.5 text-[12px]">
              <span className={cn('badge text-[10px]', r.revenueType === 'COMMISSION' ? 'bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400' : 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400')}>
                {r.revenueType.replace('_', ' ')}
              </span>
              <span className="text-muted flex-1 truncate">{fmt.datetime(r.createdAt)}</span>
              <span className="text-gray-600 dark:text-gray-300">{fmt.currency(r.grossAmount)} gross</span>
              <span className="font-semibold text-gray-900 dark:text-white w-20 text-right">{fmt.currency(r.platformFee)}</span>
              <StatusBadge status={r.status} size="sm"/>
            </div>
          ))}
          {revenue.length === 0 && <p className="text-center text-[12px] text-muted py-8">No transactions recorded yet</p>}
        </div>
      </Card>
    </div>
  )
}
