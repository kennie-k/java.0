import { useMemo, useState } from 'react'
import { useQueries } from '@tanstack/react-query'
import { revenueApi, userApi, propertyApi, verifApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'

const OWN_STATUSES = ['MINISTRY_LANDS_CHECK', 'ENCUMBRANCE_CHECK', 'LEGAL_REVIEW', 'HUMAN_REVIEW'] as const

export function useAdminOverview() {
  const [query, setQuery] = useState('')

  const [summaryQ, revenueQ, usersQ, propertiesQ, idQueueQ] = useQueries({
    queries: [
      { queryKey: queryKeys.revenueSummary, queryFn: () => revenueApi.summary() },
      { queryKey: queryKeys.revenueAll, queryFn: () => revenueApi.all() },
      { queryKey: queryKeys.users, queryFn: () => userApi.allAdmin() },
      { queryKey: queryKeys.propertiesForOverview, queryFn: () => propertyApi.search({ size: 500 }) },
      { queryKey: queryKeys.identityAdminQueue('HUMAN_REVIEW'), queryFn: () => verifApi.idAdminQueue() },
    ],
  })

  const ownQueueQs = useQueries({
    queries: OWN_STATUSES.map(status => ({
      queryKey: queryKeys.ownershipAdminQueue(status),
      queryFn: () => verifApi.ownerAdminQueue(status),
    })),
  })

  const loading = summaryQ.isLoading || revenueQ.isLoading || usersQ.isLoading
    || propertiesQ.isLoading || idQueueQ.isLoading || ownQueueQs.some(q => q.isLoading)

  const summary = summaryQ.data ?? null
  const revenue = revenueQ.data?.content ?? []
  const users = usersQ.data?.content ?? []
  const properties = propertiesQ.data?.content ?? []
  const idQueue = idQueueQ.data?.content ?? []
  const ownQueue = useMemo(
    () => ownQueueQs.flatMap(q => q.data?.content ?? []),
    [ownQueueQs]
  )

  const stats = useMemo(() => {
    const buyers = users.filter(u => u.role === 'BUYER').length
    const sellers = users.filter(u => u.role === 'SELLER').length
    const agents = users.filter(u => u.role === 'AGENT').length
    const admins = users.filter(u => u.role === 'ADMIN').length
    const verifiedUsers = users.filter(u => u.verified).length
    const verifiedPct = users.length ? Math.round((verifiedUsers / users.length) * 100) : 0

    const active = properties.filter(p => p.status === 'ACTIVE').length
    const draft = properties.filter(p => p.status === 'DRAFT').length
    const pending = properties.filter(p => p.status === 'PENDING_VERIFICATION').length
    const closed = properties.filter(p => ['SOLD', 'RENTED'].includes(p.status)).length
    const fullyTrusted = properties.filter(p => p.fullyTrusted).length
    const avgPrice = properties.length ? properties.reduce((s, p) => s + p.price, 0) / properties.length : 0
    const totalViews = properties.reduce((s, p) => s + (p.viewCount || 0), 0)

    const byType: Record<string, number> = {}
    properties.forEach(p => { byType[p.propertyType] = (byType[p.propertyType] || 0) + 1 })
    const typeChart = Object.entries(byType).map(([name, value]) => ({ name, value }))

    const byCounty: Record<string, number> = {}
    properties.forEach(p => { byCounty[p.county] = (byCounty[p.county] || 0) + 1 })
    const topCounties = Object.entries(byCounty).sort((a, b) => b[1] - a[1]).slice(0, 6)

    const commission = revenue.filter(r => r.revenueType === 'COMMISSION').reduce((s, r) => s + r.platformFee, 0)
    const viewingFee = revenue.filter(r => r.revenueType === 'VIEWING_FEE').reduce((s, r) => s + r.platformFee, 0)
    const pendingPayout = revenue.filter(r => r.status !== 'PAYOUT_COMPLETED').length
    const totalPayout = revenue.reduce((s, r) => s + r.sellerPayout, 0)

    const bySellerPayout: Record<string, number> = {}
    revenue.forEach(r => { bySellerPayout[r.sellerId] = (bySellerPayout[r.sellerId] || 0) + r.sellerPayout })
    const topSellers = Object.entries(bySellerPayout).sort((a, b) => b[1] - a[1]).slice(0, 5)

    const byMonth: Record<string, number> = {}
    revenue.forEach(r => {
      const key = new Date(r.createdAt).toLocaleDateString('en', { month: 'short' })
      byMonth[key] = (byMonth[key] || 0) + r.platformFee
    })
    const trendChart = Object.entries(byMonth).map(([name, value]) => ({ name, value }))

    const fraudStrikes = idQueue.reduce((s, v) => s + (v.fraudStrikeCount || 0), 0)
    const banned = idQueue.filter(v => v.permanentlyBanned).length

    return {
      buyers, sellers, agents, admins, verifiedUsers, verifiedPct,
      active, draft, pending, closed, fullyTrusted, avgPrice, totalViews,
      typeChart, topCounties, commission, viewingFee, pendingPayout, totalPayout,
      topSellers, trendChart, fraudStrikes, banned,
    }
  }, [users, properties, revenue, idQueue])

  const searchResults = useMemo(() => {
    if (!query.trim()) return null
    const q = query.toLowerCase()
    const matchedUsers = users.filter(u => u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)).slice(0, 5)
    const matchedProperties = properties.filter(p => p.title.toLowerCase().includes(q) || p.county.toLowerCase().includes(q)).slice(0, 5)
    return { users: matchedUsers, properties: matchedProperties }
  }, [query, users, properties])

  const growth = summary && summary.lastMonthRevenue
    ? Math.round(((summary.thisMonthRevenue - summary.lastMonthRevenue) / summary.lastMonthRevenue) * 100)
    : 0

  return {
    loading, summary, revenue, users, properties, idQueue, ownQueue,
    stats, query, setQuery, searchResults, growth,
  }
}
