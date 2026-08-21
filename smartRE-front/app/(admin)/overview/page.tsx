'use client'
import Link from 'next/link'
import {
  DollarSign, TrendingUp, TrendingDown, Building2, ShieldCheck, ShieldAlert,
  Search, ArrowUpRight, Landmark, Clock, Star, ListChecks, PieChart as PieChartIcon,
} from 'lucide-react'
import { useAdminOverview } from '@/hooks/useAdminOverview'
import { Card, StatCard } from '@/components/ui/Card'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import { fmt, cn } from '@/lib/utils'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts'

const PIE_COLORS = ['#C9A227', '#3B82F6', '#10B981', '#8B5CF6', '#F59E0B']

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <p className="text-[10.5px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600 px-0.5">{children}</p>
}

function AttentionCard({ icon, tone, label, value, sub, href }:
  { icon: React.ReactNode; tone: 'amber'|'red'; label: string; value: React.ReactNode; sub: string; href: string }) {
  const tones = {
    amber: { border: 'border-l-amber-400 dark:border-l-amber-500/60', icon: 'text-amber-500 bg-amber-50 dark:bg-amber-500/10' },
    red:   { border: 'border-l-red-400 dark:border-l-red-500/60', icon: 'text-red-500 bg-red-50 dark:bg-red-500/10' },
  }[tone]
  return (
    <Card className={cn('border-l-[3px] card-hover', tones.border)}>
      <div className="flex items-start justify-between gap-2 mb-2.5">
        <div className={cn('w-8 h-8 rounded-lg flex items-center justify-center', tones.icon)}>{icon}</div>
        <Link href={href} className="text-[11px] text-gold-500 hover:text-gold-600 font-medium flex items-center gap-0.5 mt-1">Review <ArrowUpRight size={11}/></Link>
      </div>
      <p className="text-[22px] font-semibold text-gray-900 dark:text-white leading-none tabular-nums">{value}</p>
      <p className="text-[11px] font-medium text-gray-700 dark:text-gray-300 mt-2">{label}</p>
      <p className="text-[11px] text-muted mt-0.5">{sub}</p>
    </Card>
  )
}

export default function AdminOverview() {
  const { loading, error, summary, revenue, users, properties, idQueue, ownQueue, stats, query, setQuery, searchResults, growth } = useAdminOverview()

  if (loading) return <PageLoader/>

  return (
    <div className="space-y-7 pb-10">
      {error && <InlineError message="Some dashboard data failed to load — figures below may be incomplete."/>}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Command Center</h1>
          <p className="text-[12.5px] text-muted mt-1">{users.length} users · {properties.length} listings · {fmt.currency(summary?.totalPlatformFees || 0)} lifetime revenue</p>
        </div>
        <div className="relative w-full sm:w-72">
          <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
          <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search users, properties, counties..."
            className="w-full h-9 pl-8 pr-3 rounded-lg bg-gray-50 dark:bg-white/5 text-[12px] placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-gold-500/20 border border-base focus:border-gold-500 transition-all"/>
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

      <div className="space-y-2.5">
        <SectionLabel>Overview</SectionLabel>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <StatCard label="Lifetime revenue" value={fmt.currency(summary?.totalPlatformFees || 0)}
            sub={growth === null
              ? <span className="text-muted">No revenue last month to compare</span>
              : <span className={cn('flex items-center gap-0.5', growth >= 0 ? 'text-emerald-500' : 'text-red-500')}>{growth >= 0 ? <TrendingUp size={11}/> : <TrendingDown size={11}/>}{Math.abs(growth)}% vs last month</span>}
            icon={<DollarSign size={16}/>} color="gold"/>
          <StatCard label="Active listings" value={stats.active} sub={`${stats.pending} pending · ${stats.draft} draft`} icon={<Building2 size={16}/>} color="blue"/>
          <StatCard label="Verified users" value={`${stats.verifiedPct}%`} sub={`${stats.buyers} buyers · ${stats.sellers} sellers · ${stats.agents} agents`} icon={<ShieldCheck size={16}/>} color="emerald"/>
          <StatCard label="Escrow pending" value={stats.pendingPayout}
            sub={stats.failedPayout > 0 ? `${fmt.currency(stats.totalPayout)} in payouts · ${stats.failedPayout} failed` : `${fmt.currency(stats.totalPayout)} in payouts`}
            icon={<Clock size={16}/>} color="purple"/>
        </div>
      </div>

      <div className="space-y-2.5">
        <SectionLabel>Needs attention</SectionLabel>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <AttentionCard icon={<ListChecks size={15}/>} tone="amber" label="Identity queue" sub="awaiting human review"
            value={idQueue.length} href="/verification-queue"/>
          <AttentionCard icon={<Landmark size={15}/>} tone="amber" label="Ownership queue" sub="Ardhisasa / legal review pending"
            value={ownQueue.length} href="/verification-queue"/>
          <AttentionCard icon={<ShieldAlert size={15}/>} tone="red" label="Fraud strikes" sub={`${stats.banned} permanently banned`}
            value={stats.fraudStrikes} href="/verification-queue"/>
        </div>
      </div>

      <div className="space-y-2.5">
        <SectionLabel>Revenue</SectionLabel>
        <div className="grid lg:grid-cols-3 gap-4">
          <Card className="lg:col-span-2">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2"><TrendingUp size={14} className="text-muted"/>Revenue trend</h2>
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
                  <Tooltip formatter={(v: any) => [fmt.currency(v), 'Revenue']} contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                  <Area type="monotone" dataKey="value" stroke="#C9A227" strokeWidth={2} fill="url(#rev)"/>
                </AreaChart>
              </ResponsiveContainer>
            )}
          </Card>

          <Card>
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2 mb-3"><PieChartIcon size={14} className="text-muted"/>Revenue split</h2>
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
                  <Tooltip formatter={(v: any) => fmt.currency(v)} contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                </PieChart>
              </ResponsiveContainer>
            )}
            <div className="flex items-center justify-center gap-4 text-[11px] mt-1">
              <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-gold-500"/>Commission</span>
              <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-blue-500"/>Viewing fees</span>
            </div>
          </Card>
        </div>
      </div>

      <div className="space-y-2.5">
        <SectionLabel>Marketplace</SectionLabel>
        <div className="grid lg:grid-cols-3 gap-4">
          <Card>
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white mb-3">Listings by property type</h2>
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
                  <Tooltip contentStyle={{ borderRadius: 10, fontSize: 12, background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}/>
                  <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                    {stats.typeChart.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]}/>)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            )}
            <div className="flex flex-wrap gap-x-3 gap-y-1 mt-2 text-[10.5px] text-muted">
              <span>Avg price: <b className="text-gray-900 dark:text-white font-medium">{fmt.currency(stats.avgPrice)}</b></span>
              <span>Fully trusted: <b className="text-gray-900 dark:text-white font-medium">{stats.fullyTrusted}</b></span>
              <span>Closed: <b className="text-gray-900 dark:text-white font-medium">{stats.closed}</b></span>
            </div>
          </Card>

          <Card>
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white mb-3">Top counties</h2>
            <div className="space-y-2.5">
              {stats.topCounties.length === 0 && <p className="text-[11px] text-muted">No listings yet</p>}
              {stats.topCounties.map(([county, count]) => (
                <div key={county} className="flex items-center gap-2">
                  <span className="text-[12px] text-gray-700 dark:text-gray-300 w-20 truncate">{county}</span>
                  <div className="flex-1 h-1.5 rounded-full bg-gray-100 dark:bg-white/5 overflow-hidden">
                    <div className="h-full bg-gold-500 rounded-full" style={{ width: `${(count / (stats.topCounties[0]?.[1] || 1)) * 100}%` }}/>
                  </div>
                  <span className="text-[11px] text-muted w-6 text-right tabular-nums">{count}</span>
                </div>
              ))}
            </div>
          </Card>

          <Card>
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white flex items-center gap-2 mb-3"><Star size={14} className="text-muted"/>Top sellers by payout</h2>
            <div className="space-y-2.5">
              {stats.topSellers.length === 0 && <p className="text-[11px] text-muted">No payouts yet</p>}
              {stats.topSellers.map(([id, amt], i) => (
                <div key={id} className="flex items-center gap-2.5 text-[12px]">
                  <span className="w-5 h-5 rounded-full bg-gray-50 dark:bg-white/5 text-muted text-[10px] font-semibold flex items-center justify-center shrink-0">{i + 1}</span>
                  <span className="flex-1 truncate font-mono text-[11px] text-gray-600 dark:text-gray-300">{id.slice(0, 8)}…</span>
                  <span className="font-semibold text-gray-900 dark:text-white tabular-nums">{fmt.currency(amt)}</span>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      <div className="space-y-2.5">
        <SectionLabel>Ledger</SectionLabel>
        <Card padding="none">
          <div className="flex items-center justify-between p-4 pb-0">
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white">Recent transactions</h2>
            <Link href="/revenue" className="text-[11px] text-gold-500 hover:text-gold-600 flex items-center gap-0.5 font-medium">Full ledger <ArrowUpRight size={11}/></Link>
          </div>
          <div className="divide-y divide-gray-100 dark:divide-[#1E1E3A] mt-3">
            {revenue.slice(0, 8).map(r => (
              <div key={r.id} className="flex items-center gap-3 px-4 py-2.5 text-[12px] hover:bg-gray-50/60 dark:hover:bg-white/[0.02] transition-colors">
                <Badge size="sm" variant={r.revenueType === 'TRANSACTION_COMMISSION' ? 'gold' : 'info'}>
                  {r.revenueType.replace('_', ' ')}
                </Badge>
                <span className="text-muted flex-1 truncate">{fmt.datetime(r.createdAt)}</span>
                <span className="text-gray-600 dark:text-gray-300 tabular-nums">{fmt.currency(r.grossAmount)} gross</span>
                <span className="font-semibold text-gray-900 dark:text-white w-20 text-right tabular-nums">{fmt.currency(r.platformFee)}</span>
                <StatusBadge status={r.status} size="sm"/>
              </div>
            ))}
            {revenue.length === 0 && <p className="text-center text-[12px] text-muted py-8">No transactions recorded yet</p>}
          </div>
        </Card>
      </div>
    </div>
  )
}
