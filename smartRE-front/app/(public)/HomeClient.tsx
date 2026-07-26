'use client'
import { useEffect, useState, FormEvent } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import {
  Search, MapPin, Home as HomeIcon, Building, Trees, Store, ShieldCheck,
  Smartphone, Landmark, FileCheck, ArrowRight, Star, TrendingUp, Users, Building2,
} from 'lucide-react'
import { propertyApi } from '@/lib/api'
import type { PropertyResponse } from '@/types'
import PropertyCard from '@/components/property/PropertyCard'
import RevealCard from '@/components/ui/RevealCard'
import { SkeletonCard } from '@/components/ui/Modal'

const categories = [
  { label: 'Houses',     type: 'HOUSE',      icon: HomeIcon },
  { label: 'Apartments', type: 'APARTMENT',  icon: Building },
  { label: 'Land',       type: 'LAND',       icon: Trees },
  { label: 'Commercial', type: 'COMMERCIAL', icon: Store },
  { label: 'Villas',     type: 'VILLA',      icon: Building2 },
]

const steps = [
  {
    n: '01', title: 'Seller proves who they are',
    desc: 'National ID, KRA PIN and a selfie are cross-checked. Every document hash is compared against every document ever submitted. A reused ID is caught instantly and flagged as fraud.',
    icon: ShieldCheck,
  },
  {
    n: '02', title: 'Ownership is confirmed with the Ministry of Lands',
    desc: 'Title deed, parcel number and county are checked against Ardhisasa. If there\u2019s a caveat, court order or bank charge on the title, the listing does not go live.',
    icon: Landmark,
  },
  {
    n: '03', title: 'You pay through M-Pesa escrow, never the seller directly',
    desc: 'STK Push to your phone, funds held in escrow, and released only when the deal is settled. Every transaction gets a receipt and a full audit trail.',
    icon: Smartphone,
  },
]

const trustPoints = [
  { icon: FileCheck, label: 'Ardhisasa-checked titles', detail: 'Every ownership claim is verified against the Ministry of Lands registry, not just the seller\u2019s word.' },
  { icon: ShieldCheck, label: 'Two-layer identity checks', detail: 'National ID + KRA PIN verification, with a fraud-strike system that permanently bans repeat offenders.' },
  { icon: Smartphone, label: 'M-Pesa escrow, not seller accounts', detail: 'Money sits in escrow via Safaricom Daraja until the transaction is fully confirmed on both sides.' },
]

const fadeUp = (delay = 0) => ({
  initial: { opacity: 0, y: 18 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.5, delay, ease: [0.22, 1, 0.36, 1] as const },
})

export default function HomeClient() {
  const router = useRouter()
  const [keyword, setKeyword] = useState('')
  const [county, setCounty] = useState('')
  const [listingType, setListingType] = useState('')
  const [featured, setFeatured] = useState<PropertyResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState({ total: 0, verified: 0, counties: 0 })

  useEffect(() => {
    propertyApi.search({ size: 8, sort: 'createdAt,desc' })
      .then(r => {
        const content = r.content || []
        setFeatured(content)
        const verified = content.filter(p => p.fullyTrusted).length
        const uniqCounties = new Set(content.map(p => p.county)).size
        setStats({ total: r.totalElements || content.length, verified, counties: uniqCounties || 0 })
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const onSearch = (e: FormEvent) => {
    e.preventDefault()
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (county) params.set('county', county)
    if (listingType) params.set('listingType', listingType)
    router.push(`/properties${params.toString() ? `?${params.toString()}` : ''}`)
  }

  return (
    <>
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-gold-50 via-white to-amber-50 dark:from-gold-500/10 dark:via-[#0B0B18] dark:to-[#0B0B18] -z-10"/>
        <div className="absolute -top-24 -right-24 w-96 h-96 bg-gold-200/40 dark:bg-gold-500/10 rounded-full blur-3xl -z-10"/>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 pt-12 pb-10 sm:pt-16 sm:pb-14">
          <div className="max-w-2xl">
            <motion.span {...fadeUp(0)} className="inline-flex items-center gap-1.5 badge bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400 mb-3 text-[10px]">
              <ShieldCheck size={10}/> Identity + title verified, every listing
            </motion.span>
            <motion.h1 {...fadeUp(0.08)} className="font-display text-2xl sm:text-4xl font-bold text-gray-900 dark:text-white leading-[1.15] mb-3">
              Find property in Kenya without wondering who you&apos;re paying.
            </motion.h1>
            <motion.p {...fadeUp(0.16)} className="text-[13px] text-muted mb-6 max-w-xl">
              Every seller on SmartRE has proven their National ID, KRA PIN and Ardhisasa land title before a listing
              ever goes live. Pay safely through M-Pesa escrow, never straight to a stranger&apos;s phone.
            </motion.p>
          </div>

          <motion.form {...fadeUp(0.24)} onSubmit={onSearch} className="card p-2 flex flex-col sm:flex-row gap-1.5 max-w-3xl">
            <div className="relative flex-1">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
              <input value={keyword} onChange={e => setKeyword(e.target.value)}
                placeholder="Search by title, estate, or keyword..."
                className="w-full h-9 pl-8 pr-3 rounded-md bg-transparent text-[13px] placeholder:text-gray-400 transition-all focus:outline-none focus:ring-2 focus:ring-gold-500/30 focus:bg-white dark:focus:bg-white/10"/>
            </div>
            <div className="relative sm:w-40">
              <MapPin size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
              <input value={county} onChange={e => setCounty(e.target.value)}
                placeholder="County"
                className="w-full h-9 pl-8 pr-3 rounded-md bg-gray-50 dark:bg-white/5 text-[13px] placeholder:text-gray-400 transition-all focus:outline-none focus:ring-2 focus:ring-gold-500/30 focus:bg-white dark:focus:bg-white/10"/>
            </div>
            <select value={listingType} onChange={e => setListingType(e.target.value)}
              className="h-9 px-2.5 rounded-md bg-gray-50 dark:bg-white/5 text-[13px] text-gray-700 dark:text-gray-200 transition-all focus:outline-none focus:ring-2 focus:ring-gold-500/30 sm:w-32">
              <option value="">Buy or rent</option>
              <option value="SALE">For sale</option>
              <option value="RENT">To rent</option>
            </select>
            <button type="submit" className="btn-primary sm:w-auto justify-center">
              <Search size={13}/> Search
            </button>
          </motion.form>

          <motion.div {...fadeUp(0.32)} className="flex flex-wrap items-center gap-x-6 gap-y-2 mt-6 text-[13px]">
            <div className="flex items-center gap-1.5">
              <TrendingUp size={13} className="text-gold-500"/>
              <span className="font-display font-bold text-gray-900 dark:text-white">{stats.total.toLocaleString()}</span>
              <span className="text-muted text-[12px]">live listings</span>
            </div>
            <div className="flex items-center gap-1.5">
              <ShieldCheck size={13} className="text-emerald-500"/>
              <span className="font-display font-bold text-gray-900 dark:text-white">7</span>
              <span className="text-muted text-[12px]">verification checks per seller</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Users size={13} className="text-blue-500"/>
              <span className="font-display font-bold text-gray-900 dark:text-white">2.5%</span>
              <span className="text-muted text-[12px]">flat commission, no hidden fees</span>
            </div>
          </motion.div>
        </div>
      </section>

      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2.5">
          {categories.map((c, i) => (
            <RevealCard key={c.type} index={i}>
              <Link href={`/properties?propertyType=${c.type}`}
                className="card-hover flex flex-col items-center justify-center gap-1.5 py-5 text-center">
                <div className="w-8 h-8 rounded-lg bg-gold-50 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center">
                  <c.icon size={15}/>
                </div>
                <span className="text-[11px] font-medium text-gray-700 dark:text-gray-200">{c.label}</span>
              </Link>
            </RevealCard>
          ))}
        </div>
      </section>

      <section className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="flex items-end justify-between mb-4">
          <div>
            <h2 className="font-display text-lg sm:text-xl font-bold text-gray-900 dark:text-white">Newest verified listings</h2>
            <p className="text-[12px] text-muted mt-0.5">Fresh onto the market, with identity and ownership already confirmed.</p>
          </div>
          <Link href="/properties" className="hidden sm:flex items-center gap-1 text-[12px] font-medium text-gold-500 hover:text-gold-600">
            View all <ArrowRight size={12}/>
          </Link>
        </div>
        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
            {Array(4).fill(0).map((_, i) => <SkeletonCard key={i}/>)}
          </div>
        ) : featured.length === 0 ? (
          <div className="card p-8 text-center text-muted text-[13px]">New listings are being verified. Check back soon.</div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
            {featured.map((p, i) => <RevealCard key={p.id} index={i}><PropertyCard property={p} priority={i < 4}/></RevealCard>)}
          </div>
        )}
        <Link href="/properties" className="sm:hidden btn-secondary w-full justify-center mt-4">View all listings</Link>
      </section>

      <section id="how-it-works" className="bg-surface-2 border-y border-base py-12 mt-3">
        <div className="max-w-7xl mx-auto px-4 sm:px-6">
          <div className="max-w-xl mb-7">
            <span className="text-[10px] font-semibold uppercase tracking-widest text-gold-500">How SmartRE works</span>
            <h2 className="font-display text-xl sm:text-2xl font-bold text-gray-900 dark:text-white mt-1.5">
              Three checks stand between a listing and your money.
            </h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {steps.map((s, i) => (
              <RevealCard key={s.n} index={i}>
                <div className="card p-5 relative h-full">
                  <span className="font-display text-3xl font-bold text-gold-100 dark:text-gold-500/10 absolute top-3 right-4">{s.n}</span>
                  <div className="w-8 h-8 rounded-lg bg-gold-50 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center mb-3">
                    <s.icon size={15}/>
                  </div>
                  <h3 className="font-display font-semibold text-gray-900 dark:text-white text-[14px] mb-1.5">{s.title}</h3>
                  <p className="text-[12px] text-muted leading-relaxed">{s.desc}</p>
                </div>
              </RevealCard>
            ))}
          </div>
        </div>
      </section>

      <section id="trust" className="max-w-7xl mx-auto px-4 sm:px-6 py-12">
        <div className="max-w-xl mb-7">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-gold-500">Why it&apos;s different</span>
          <h2 className="font-display text-xl sm:text-2xl font-bold text-gray-900 dark:text-white mt-1.5">
            Built for a market where anyone can list a property they don&apos;t own.
          </h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {trustPoints.map((t, i) => (
            <RevealCard key={t.label} index={i}>
              <div className="card p-5 h-full">
                <div className="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-3">
                  <t.icon size={15}/>
                </div>
                <h3 className="font-semibold text-gray-900 dark:text-white text-[13px] mb-1">{t.label}</h3>
                <p className="text-[12px] text-muted leading-relaxed">{t.detail}</p>
              </div>
            </RevealCard>
          ))}
        </div>
      </section>

      <section className="max-w-7xl mx-auto px-4 sm:px-6 pb-16">
        <RevealCard>
          <div className="rounded-2xl bg-gradient-to-br from-gold-500 to-gold-600 p-6 sm:p-9 flex flex-col sm:flex-row items-center justify-between gap-5 relative overflow-hidden">
            <div className="absolute -right-10 -top-10 w-48 h-48 bg-white/10 rounded-full"/>
            <div className="relative">
              <h2 className="font-display text-lg sm:text-xl font-bold text-white mb-1.5">Selling a property?</h2>
              <p className="text-gold-50 text-[13px] max-w-md">
                List for free. You only pay a 2.5% commission when a sale actually closes, with verified buyers and secure M-Pesa payouts.
              </p>
            </div>
            <Link href="/register" className="relative bg-white text-gold-600 hover:bg-gold-50 h-9 px-5 rounded-lg font-semibold text-[13px] inline-flex items-center gap-2 shrink-0 transition-colors">
              Start selling <ArrowRight size={13}/>
            </Link>
          </div>
        </RevealCard>
      </section>
    </>
  )
}
