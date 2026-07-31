'use client'
import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Ban, RotateCcw, AlertTriangle, ExternalLink, Search } from 'lucide-react'
import { propertyApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticPatchInPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { PropertyResponse, ListingStatus } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import { ConfirmModal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import { StatusBadge } from '@/components/ui/Badge'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PENDING_VERIFICATION', label: 'Pending verification' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SUSPENDED', label: 'Suspended' },
]

const VALID_STATUSES = STATUS_OPTIONS.map(o => o.value)

export default function AdminListingsPage() {
  return <Suspense fallback={<PageLoader/>}><AdminListingsPageInner/></Suspense>
}

function AdminListingsPageInner() {
  const qc = useQueryClient()
  const sp = useSearchParams()
  const statusParam = sp.get('status')
  const [status, setStatus] = useState<ListingStatus | ''>(
    statusParam && VALID_STATUSES.includes(statusParam) ? statusParam as ListingStatus : ''
  )
  useEffect(() => {
    setStatus(statusParam && VALID_STATUSES.includes(statusParam) ? statusParam as ListingStatus : '')
  }, [statusParam])
  const [search, setSearch] = useState('')
  const [target, setTarget] = useState<PropertyResponse | null>(null)

  const queryKey = queryKeys.listingsAdmin(status || 'ALL')
  const { data, isLoading: loading, error } = useQuery({
    queryKey,
    queryFn: () => propertyApi.adminAll(status ? { status } : undefined),
  })
  const items = data?.content ?? []
  const filtered = items.filter(p =>
    !search || p.title.toLowerCase().includes(search.toLowerCase()) || p.county.toLowerCase().includes(search.toLowerCase())
  )

  const toggleMutation = useMutation({
    mutationFn: (item: PropertyResponse) => item.status === 'SUSPENDED'
      ? propertyApi.adminReactivate(item.id)
      : propertyApi.adminSuspend(item.id),
    onMutate: item => optimisticPatchInPage<PropertyResponse>(qc, queryKey, item.id,
      item.status === 'SUSPENDED' ? { status: 'ACTIVE', duplicateParcelFlag: false } : { status: 'SUSPENDED' }),
    onError: (_e, _item, previous) => rollbackPage(qc, queryKey, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.listingsAdmin(status || 'ALL') }),
  })

  const confirmAction = async () => {
    if (!target) return
    try {
      await toggleMutation.mutateAsync(target)
      toast.success(target.status === 'SUSPENDED' ? 'Listing reactivated' : 'Listing suspended')
      setTarget(null)
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Action failed')
    }
  }

  if (loading) return <PageLoader/>

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Listings</h1>
          <p className="text-muted text-[13px] mt-1">{items.length} listing{items.length !== 1 ? 's' : ''}</p>
        </div>
        <div className="w-56">
          <Select label="Status" options={STATUS_OPTIONS} value={status} onChange={e => setStatus(e.target.value as ListingStatus)}/>
        </div>
      </div>

      {error && <InlineError message="Failed to load listings."/>}

      {items.length > 0 && (
        <Input leftIcon={<Search size={15}/>} placeholder="Search by title or county..." value={search} onChange={e => setSearch(e.target.value)}/>
      )}

      {items.length === 0 ? (
        <EmptyState icon={<Building2 size={28}/>} title="No listings found"/>
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search size={24}/>} title="No listings match that search"/>
      ) : (
        <div className="space-y-3">
          {filtered.map(p => (
            <Card key={p.id}>
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  <Building2 size={17}/>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 dark:text-white text-[13px]">{p.title}</p>
                    <StatusBadge status={p.status} size="sm"/>
                    {p.duplicateParcelFlag && (
                      <span className="badge bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400 text-[10px]">
                        <AlertTriangle size={10}/>Duplicate parcel
                      </span>
                    )}
                  </div>
                  <p className="text-[12px] text-muted">
                    {p.county} · {fmt.currency(p.price)} · {p.parcelNumber || 'no parcel #'} · {fmt.date(p.createdAt)}
                  </p>
                  <a href={`/properties/${p.id}`} target="_blank" rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-[11px] text-gold-600 dark:text-gold-400 mt-1.5 hover:underline">
                    View listing <ExternalLink size={10}/>
                  </a>
                </div>
                <Button size="sm" variant={p.status === 'SUSPENDED' ? 'secondary' : 'danger'}
                  leftIcon={p.status === 'SUSPENDED' ? <RotateCcw size={13}/> : <Ban size={13}/>}
                  onClick={() => setTarget(p)}>
                  {p.status === 'SUSPENDED' ? 'Reactivate' : 'Suspend'}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <ConfirmModal open={!!target} onClose={() => setTarget(null)} onConfirm={confirmAction} loading={toggleMutation.isPending}
        danger={target?.status !== 'SUSPENDED'} label={target?.status === 'SUSPENDED' ? 'Reactivate' : 'Suspend'}
        title={target?.status === 'SUSPENDED' ? 'Reactivate listing' : 'Suspend listing'}
        message={target ? (target.status === 'SUSPENDED'
          ? `Reactivate "${target.title}"? It will become visible in search again.`
          : `Suspend "${target.title}"? It will be hidden from search and buyers immediately.`) : ''}/>
    </div>
  )
}
