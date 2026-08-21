import { useMemo, useState } from 'react'
import { useQueries } from '@tanstack/react-query'
import { revenueApi, userApi, propertyApi, verifApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'

const OWN_STATUSES = ['MINISTRY_LANDS_CHECK', 'ENCUMBRANCE_CHECK', 'LEGAL_REVIEW', 'HUMAN_REVIEW'] as const

export function useAdminOverview() {
  const [query, setQuery] = useState('')

  const [summaryQ, revenueQ, usersQ, propertiesQ, idQueueQ, fraudSummaryQ, userStatsQ, propertyStatsQ] = useQueries({
    queries: [
      { queryKey: queryKeys.revenueSummary, queryFn: () => revenueApi.summary(), refetchInterval: 15_000 },
      { queryKey: queryKeys.revenueAll, queryFn: () => revenueApi.all(), refetchInterval: 15_000 },
      // This feeds the overview page's global quick-search box (jump to any
      // user by name/email), not a paginated list — it intentionally pulls a
      // large batch rather than paging through results. The dedicated Users
      // management page (hooks/useUsers.ts) paginates for real instead.
      { queryKey: queryKeys.users, queryFn: () => userApi.allAdmin(0, 500), refetchInterval: 15_000 },
      { queryKey: queryKeys.propertiesForOverview, queryFn: () => propertyApi.search({ size: 500 }), refetchInterval: 15_000 },
      { queryKey: queryKeys.identityAdminQueue('HUMAN_REVIEW'), queryFn: () => verifApi.idAdminQueue(), refetchInterval: 15_000 },
      { queryKey: queryKeys.identityFraudSummary, queryFn: () => verifApi.idFraudSummary(), refetchInterval: 15_000 },
      { queryKey: queryKeys.userAdminStats, queryFn: () => userApi.adminStats(), refetchInterval: 15_000 },
      { queryKey: queryKeys.propertyAdminStats, queryFn: () => propertyApi.adminStats(), refetchInterval: 15_000 },
    ],
  })

  const ownQueueQs = useQueries({
    queries: OWN_STATUSES.map(status => ({
      queryKey: queryKeys.ownershipAdminQueue(status),
      queryFn: () => verifApi.ownerAdminQueue(status),
      refetchInterval: 15_000,
    })),
  })

  const loading = summaryQ.isLoading || revenueQ.isLoading || usersQ.isLoading
    || propertiesQ.isLoading || idQueueQ.isLoading || fraudSummaryQ.isLoading
    || userStatsQ.isLoading || propertyStatsQ.isLoading || ownQueueQs.some(q => q.isLoading)
  const error = summaryQ.isError || revenueQ.isError || usersQ.isError
    || propertiesQ.isError || idQueueQ.isError || fraudSummaryQ.isError
    || userStatsQ.isError || propertyStatsQ.isError || ownQueueQs.some(q => q.isError)

  const summary = summaryQ.data ?? null
  const revenue = revenueQ.data?.content ?? []
  const users = usersQ.data?.content ?? []
  const properties = propertiesQ.data?.content ?? []
  const idQueue = idQueueQ.data?.content ?? []
  const fraudSummary = fraudSummaryQ.data ?? { totalFraudStrikes: 0, permanentlyBanned: 0 }
  const userStats = userStatsQ.data ?? { buyers: 0, sellers: 0, agents: 0, admins: 0, total: 0, verified: 0 }
  const propertyStats = propertyStatsQ.data ?? {
    active: 0, draft: 0, pendingVerification: 0, sold: 0, rented: 0, suspended: 0, withdrawn: 0,
    avgActivePrice: 0, totalViews: 0, byType: [], topCounties: [],
  }
  const ownQueue = useMemo(
    () => ownQueueQs.flatMap(q => q.data?.content ?? []),
    [ownQueueQs]
  )

  const stats = useMemo(() => {
    const buyers = userStats.buyers
    const sellers = userStats.sellers
    const agents = userStats.agents
    const admins = userStats.admins
    const verifiedUsers = userStats.verified
    const verifiedPct = userStats.total ? Math.round((userStats.verified / userStats.total) * 100) : 0

    const active = propertyStats.active
    const draft = propertyStats.draft
    const pending = propertyStats.pendingVerification
    const closed = propertyStats.sold + propertyStats.rented
    const fullyTrusted = properties.filter(p => p.fullyTrusted).length
    const avgPrice = propertyStats.avgActivePrice
    const totalViews = propertyStats.totalViews

    const typeChart = propertyStats.byType
    const topCounties = propertyStats.topCounties.map(c => [c.name, c.value] as [string, number])

    const commission = revenue.filter(r => r.revenueType === 'TRANSACTION_COMMISSION').reduce((s, r) => s + r.platformFee, 0)
    const viewingFee = revenue.filter(r => r.revenueType === 'VIEWING_FEE').reduce((s, r) => s + r.platformFee, 0)
    const pendingPayout = revenue.filter(r => r.status !== 'PAYOUT_COMPLETED' && r.status !== 'PAYOUT_FAILED').length
    const failedPayout = revenue.filter(r => r.status === 'PAYOUT_FAILED').length
    const totalPayout = revenue.reduce((s, r) => s + r.sellerPayout, 0)

    const bySellerPayout: Record<string, number> = {}
    revenue.forEach(r => { bySellerPayout[r.sellerId] = (bySellerPayout[r.sellerId] || 0) + r.sellerPayout })
    const topSellers = Object.entries(bySellerPayout).sort((a, b) => b[1] - a[1]).slice(0, 5)

    const byMonth: Record<string, { label: string; sortKey: string; value: number }> = {}
    revenue.forEach(r => {
      const d = new Date(r.createdAt)
      const sortKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      const label = d.toLocaleDateString('en', { month: 'short', year: '2-digit' })
      if (!byMonth[sortKey]) byMonth[sortKey] = { label, sortKey, value: 0 }
      byMonth[sortKey].value += r.platformFee
    })
    const trendChart = Object.values(byMonth)
      .sort((a, b) => a.sortKey.localeCompare(b.sortKey))
      .map(({ label, value }) => ({ name: label, value }))

    const fraudStrikes = fraudSummary.totalFraudStrikes
    const banned = fraudSummary.permanentlyBanned

    return {
      buyers, sellers, agents, admins, verifiedUsers, verifiedPct,
      active, draft, pending, closed, fullyTrusted, avgPrice, totalViews,
      typeChart, topCounties, commission, viewingFee, pendingPayout, failedPayout, totalPayout,
      topSellers, trendChart, fraudStrikes, banned,
    }
  }, [properties, revenue, idQueue, fraudSummary, userStats, propertyStats])

  const searchResults = useMemo(() => {
    if (!query.trim()) return null
    const q = query.toLowerCase()
    const matchedUsers = users.filter(u => u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)).slice(0, 5)
    const matchedProperties = properties.filter(p => p.title.toLowerCase().includes(q) || p.county.toLowerCase().includes(q)).slice(0, 5)
    return { users: matchedUsers, properties: matchedProperties }
  }, [query, users, properties])

  const growth = summary && summary.lastMonthRevenue
    ? Math.round(((summary.thisMonthRevenue - summary.lastMonthRevenue) / summary.lastMonthRevenue) * 100)
    : null

  return {
    loading, error, summary, revenue, users, properties, idQueue, ownQueue,
    stats, query, setQuery, searchResults, growth,
  }
}
