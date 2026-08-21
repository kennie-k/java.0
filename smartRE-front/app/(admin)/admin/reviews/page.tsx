'use client'
import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Star, EyeOff, Search } from 'lucide-react'
import { reviewApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticPatchInPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { ReviewResponse } from '@/types'
import { Card, StatCard } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import { ConfirmModal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import { Badge, RatingStars } from '@/components/ui/Badge'
import Textarea from '@/components/ui/Textarea'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const VISIBILITY_OPTIONS = [
  { value: '', label: 'All reviews' },
  { value: 'true', label: 'Visible' },
  { value: 'false', label: 'Hidden' },
]
const VALID_VISIBILITY = ['true', 'false']

export default function AdminReviewsPage() {
  return <Suspense fallback={<PageLoader/>}><AdminReviewsPageInner/></Suspense>
}

function AdminReviewsPageInner() {
  const qc = useQueryClient()
  const sp = useSearchParams()
  const visibleParam = sp.get('visible')
  const [visible, setVisible] = useState(
    visibleParam && VALID_VISIBILITY.includes(visibleParam) ? visibleParam : ''
  )
  useEffect(() => {
    setVisible(visibleParam && VALID_VISIBILITY.includes(visibleParam) ? visibleParam : '')
  }, [visibleParam])
  const [search, setSearch] = useState('')
  const [target, setTarget] = useState<ReviewResponse | null>(null)
  const [reason, setReason] = useState('')

  const statsQ = useQuery({
    queryKey: queryKeys.reviewsAdminStats,
    queryFn: () => reviewApi.adminStats(),
    refetchInterval: 15_000,
  })

  const listKey = queryKeys.reviewsAdmin(visible || 'ALL', 0)
  const listQ = useQuery({
    queryKey: listKey,
    queryFn: () => reviewApi.adminAll({ visible: visible === '' ? undefined : visible === 'true', size: 200 }),
    refetchInterval: 15_000,
  })

  const reviews = listQ.data?.content ?? []
  const filtered = reviews.filter(r =>
    !search
    || r.comment?.toLowerCase().includes(search.toLowerCase())
    || r.propertyId.toLowerCase().includes(search.toLowerCase())
    || r.sellerId.toLowerCase().includes(search.toLowerCase())
  )

  const hideMutation = useMutation({
    mutationFn: (vars: { id: string; reason: string }) => reviewApi.adminHide(vars.id, vars.reason || undefined),
    onMutate: vars => optimisticPatchInPage<ReviewResponse>(qc, listKey, vars.id, { verified: false }),
    onError: (_e, _v, previous) => rollbackPage(qc, listKey, previous),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: listKey })
      qc.invalidateQueries({ queryKey: queryKeys.reviewsAdminStats })
    },
  })

  const hide = async () => {
    if (!target) return
    try {
      await hideMutation.mutateAsync({ id: target.id, reason })
      toast.success('Review hidden')
      setTarget(null)
      setReason('')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to hide review') }
  }

  if (statsQ.isLoading || listQ.isLoading) return <PageLoader/>

  const stats = statsQ.data
  const maxCount = stats ? Math.max(1, ...stats.ratingDistribution.map(d => d.count)) : 1

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Reviews</h1>
        <p className="text-muted text-[13px] mt-1">All buyer reviews across every listing, platform-wide</p>
      </div>

      {(statsQ.isError || listQ.isError) && <InlineError message="Failed to load reviews."/>}

      {stats && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Total reviews" value={stats.totalReviews} icon={<Star size={16}/>} color="gold"/>
            <StatCard label="Average rating" value={stats.averageRating.toFixed(1)} sub="platform-wide" icon={<Star size={16}/>} color="blue"/>
            <StatCard label="Visible" value={stats.visibleReviews} icon={<Star size={16}/>} color="emerald"/>
            <StatCard label="Hidden" value={stats.hiddenReviews} sub="removed by admin" icon={<EyeOff size={16}/>} color="purple"/>
          </div>

          <Card>
            <h2 className="font-display font-semibold text-[13px] text-gray-900 dark:text-white mb-4">Rating distribution</h2>
            <div className="space-y-2">
              {[5, 4, 3, 2, 1].map(n => {
                const cnt = stats.ratingDistribution.find(d => d.rating === n)?.count ?? 0
                const pct = (cnt / maxCount) * 100
                return (
                  <div key={n} className="flex items-center gap-2 text-xs">
                    <span className="w-3 text-muted">{n}</span>
                    <div className="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                      <div className="h-full bg-amber-400 rounded-full" style={{ width: `${pct}%` }}/>
                    </div>
                    <span className="w-8 text-muted text-right">{cnt}</span>
                  </div>
                )
              })}
            </div>
          </Card>
        </>
      )}

      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex-1 min-w-[200px]">
          <Input leftIcon={<Search size={15}/>} placeholder="Search by comment, property, or seller ID..." value={search} onChange={e => setSearch(e.target.value)}/>
        </div>
        <div className="w-48">
          <Select label="Visibility" options={VISIBILITY_OPTIONS} value={visible} onChange={e => setVisible(e.target.value)}/>
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState icon={<Star size={28}/>} title="No reviews found" desc="Reviews from completed transactions will appear here."/>
      ) : (
        <div className="space-y-3">
          {filtered.map(r => (
            <Card key={r.id}>
              <div className="flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <RatingStars rating={r.rating}/>
                    <span className="text-xs text-muted">{fmt.ago(r.createdAt)}</span>
                    {r.verified ? <Badge variant="success" size="sm">Visible</Badge> : <Badge variant="error" size="sm">Hidden</Badge>}
                  </div>
                  {r.comment && <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">{r.comment}</p>}
                  <p className="text-[11px] text-muted">
                    <a href={`/properties/${r.propertyId}`} target="_blank" rel="noopener noreferrer" className="hover:underline text-gold-600 dark:text-gold-400">
                      Property {r.propertyId.slice(0, 8)}...
                    </a>
                    {' · '}
                    <a href={`/sellers/${r.sellerId}`} target="_blank" rel="noopener noreferrer" className="hover:underline text-gold-600 dark:text-gold-400">
                      Seller {r.sellerId.slice(0, 8)}...
                    </a>
                  </p>
                </div>
                {r.verified && (
                  <Button size="sm" variant="secondary" leftIcon={<EyeOff size={13}/>} onClick={() => setTarget(r)}>Hide</Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <ConfirmModal open={!!target} onClose={() => { setTarget(null); setReason('') }} onConfirm={hide}
        title="Hide review" message="This review will disappear from public listings and stop counting toward the seller's rating."
        label="Hide review" loading={hideMutation.isPending}>
        <Textarea label="Reason (optional)" value={reason} onChange={e => setReason(e.target.value)} placeholder="Reported as fake or abusive"/>
      </ConfirmModal>
    </div>
  )
}
