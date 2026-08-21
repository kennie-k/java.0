'use client'
import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Calendar, CheckCircle, XCircle, Clock, Building2 } from 'lucide-react'
import { viewingApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { isSellerOrAgent } from '@/lib/roles'
import { queryKeys } from '@/lib/queryKeys'
import type { ViewingResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, PageLoader, ConfirmModal } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import { fmt, cn } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function ViewingsPage() {
  return <Suspense fallback={<PageLoader/>}><ViewingsPageInner/></Suspense>
}

function ViewingsPageInner() {
  const { user } = useAuthStore()
  const qc = useQueryClient()
  const isSeller = isSellerOrAgent(user)
  const sp = useSearchParams()
  const tabParam = sp.get('tab')
  const [tab, setTab] = useState<'buyer'|'seller'>(
    tabParam === 'buyer' || tabParam === 'seller' ? tabParam : (isSeller ? 'seller' : 'buyer')
  )
  useEffect(() => {
    if (tabParam === 'buyer' || tabParam === 'seller') setTab(tabParam)
  }, [tabParam])
  const [confirm, setConf] = useState<{id:string;action:string}|null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const queryKey = queryKeys.myViewings(tab)
  const { data, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage, error } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => (tab === 'buyer' ? viewingApi.myBuyer(pageParam) : viewingApi.mySeller(pageParam)),
    initialPageParam: 0,
    getNextPageParam: last => {
      const next = last.number + 1
      return next < last.totalPages ? next : undefined
    },
    refetchInterval: 15_000,
  })
  const items = data?.pages.flatMap(p => p.content ?? []) ?? []

  const actionMutation = useMutation({
    mutationFn: (vars: { id: string; action: string; reason?: string }) => {
      if (vars.action === 'confirm-seller') return viewingApi.confirmSeller(vars.id)
      if (vars.action === 'confirm-buyer') return viewingApi.confirmBuyer(vars.id)
      if (vars.action === 'complete') return viewingApi.complete(vars.id)
      return viewingApi.cancel(vars.id, vars.reason?.trim() || undefined)
    },
    onSettled: () => qc.invalidateQueries({ queryKey }),
  })
  const acting = actionMutation.isPending && actionMutation.variables
    ? actionMutation.variables.id + actionMutation.variables.action
    : null

  const ACTION_SUCCESS: Record<string, string> = {
    'confirm-seller': 'Viewing confirmed',
    'confirm-buyer': 'Viewing confirmed',
    'complete': 'Viewing marked as completed',
    'cancel': 'Viewing cancelled',
  }

  const doAction = async (id: string, action: string, reason?: string) => {
    try {
      await actionMutation.mutateAsync({ id, action, reason })
      toast.success(ACTION_SUCCESS[action] || 'Viewing updated')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Action failed') }
    finally { setConf(null); setCancelReason('') }
  }

  const ACTION_MODAL: Record<string, { title: string; message: string; label: string }> = {
    'confirm-seller': { title: 'Confirm viewing', message: 'Confirm you’re available for this viewing?', label: 'Confirm viewing' },
    'confirm-buyer': { title: 'Confirm viewing', message: 'Confirm you’ll attend this viewing?', label: 'Confirm viewing' },
    'complete': { title: 'Mark as completed', message: 'Mark this viewing as completed?', label: 'Mark completed' },
    'cancel': { title: 'Cancel viewing', message: 'Are you sure you want to cancel this viewing?', label: 'Cancel viewing' },
  }
  const confirmModal = confirm ? ACTION_MODAL[confirm.action] : null

  if (isLoading) return <PageLoader/>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Viewings</h1>
        <p className="text-muted text-sm mt-1">Manage your property viewing appointments</p>
      </div>

      {error && <InlineError message="Failed to load viewings."/>}

      {!isSeller ? null : (
        <div className="flex border-b border-base">
          {(['buyer','seller'] as const).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={cn('px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors capitalize',
                tab===t ? 'border-gold-500 text-gold-500' : 'border-transparent text-muted hover:text-gray-900 dark:hover:text-white')}>
              As {t}
            </button>
          ))}
        </div>
      )}

      {items.length===0 ? (
        <EmptyState icon={<Calendar size={28}/>} title="No viewings" desc="Your viewing appointments will appear here."/>
      ) : (
        <div className="space-y-4">
          {items.map(v => <ViewingCard key={v.id} viewing={v} role={tab} onAction={(id,a) => setConf({id,action:a})} acting={acting}/>)}
        </div>
      )}

      {hasNextPage && (
        <div className="flex justify-center">
          <Button variant="secondary" size="sm" loading={isFetchingNextPage} onClick={() => fetchNextPage()}>Load more</Button>
        </div>
      )}

      <ConfirmModal open={!!confirm} onClose={() => { setConf(null); setCancelReason('') }}
        onConfirm={() => confirm && doAction(confirm.id, confirm.action, cancelReason)}
        title={confirmModal?.title ?? 'Confirm action'}
        message={confirmModal?.message ?? 'Confirm this action?'}
        label={confirmModal?.label ?? 'Confirm'}
        danger={confirm?.action==='cancel'}
        loading={actionMutation.isPending}>
        {confirm?.action === 'cancel' && (
          <textarea
            value={cancelReason}
            onChange={e => setCancelReason(e.target.value)}
            placeholder="Optional: let the other party know why (e.g. schedule conflict, property no longer available)"
            rows={3}
            className="input-base mt-3 h-auto py-2"
          />
        )}
      </ConfirmModal>
    </div>
  )
}

function ViewingCard({ viewing:v, role, onAction, acting }:{ viewing:ViewingResponse; role:string; onAction(id:string,a:string):void; acting:string|null }) {
  const canConfirmSeller = role==='seller' && v.status==='REQUESTED' && !v.sellerConfirmed
  const canConfirmBuyer  = role==='buyer'  && v.status==='REQUESTED' && !v.buyerConfirmed
  const canComplete      = role==='seller' && v.status==='CONFIRMED'
  const canCancel        = ['PENDING_FEE','REQUESTED'].includes(v.status)

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-xl bg-blue-50 dark:bg-blue-500/10 text-blue-500 flex items-center justify-center shrink-0">
            <Calendar size={22}/>
          </div>
          <div>
            <div className="flex items-center gap-2 mb-0.5">
              <p className="font-display font-semibold text-gray-900 dark:text-white">{fmt.date(v.scheduledAt)}</p>
              <StatusBadge status={v.status}/>
            </div>
            <p className="text-sm text-muted">{fmt.datetime(v.scheduledAt)}</p>
            {v.notes && <p className="text-xs text-muted mt-1 italic">&quot;{v.notes}&quot;</p>}
          </div>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-3 text-xs mr-2">
            <span className={cn('flex items-center gap-1', v.buyerConfirmed ? 'text-emerald-600' : 'text-gray-400')}>
              {v.buyerConfirmed ? <CheckCircle size={13}/> : <Clock size={13}/>} Buyer
            </span>
            <span className={cn('flex items-center gap-1', v.sellerConfirmed ? 'text-emerald-600' : 'text-gray-400')}>
              {v.sellerConfirmed ? <CheckCircle size={13}/> : <Clock size={13}/>} Seller
            </span>
          </div>

          {canConfirmSeller && <Button size="sm" onClick={() => onAction(v.id,'confirm-seller')} loading={acting===v.id+'confirm-seller'} leftIcon={<CheckCircle size={13}/>}>Confirm</Button>}
          {canConfirmBuyer  && <Button size="sm" onClick={() => onAction(v.id,'confirm-buyer')}  loading={acting===v.id+'confirm-buyer'} leftIcon={<CheckCircle size={13}/>}>Confirm</Button>}
          {canComplete      && <Button size="sm" variant="secondary" onClick={() => onAction(v.id,'complete')} loading={acting===v.id+'complete'}>Mark complete</Button>}
          {canCancel        && <Button size="sm" variant="ghost" onClick={() => onAction(v.id,'cancel')} leftIcon={<XCircle size={13}/>} className="text-red-500 hover:text-red-600">Cancel</Button>}
        </div>
      </div>

      {v.viewingFeeStatus && (
        <div className="mt-3 pt-3 border-t border-base flex items-center gap-2 text-xs text-muted">
          <span>Viewing fee:</span>
          <StatusBadge status={v.viewingFeeStatus} size="sm"/>
          {v.completedAt && <span className="ml-auto">Completed: {fmt.date(v.completedAt)}</span>}
        </div>
      )}

      {v.status === 'CANCELLED' && (
        <div className="mt-3 pt-3 border-t border-base text-xs text-muted">
          <span className="font-medium text-gray-700 dark:text-gray-300">
            Cancelled by {v.cancelledBy === v.sellerId ? 'seller' : v.cancelledBy === v.buyerId ? 'buyer' : 'system'}:
          </span>{' '}
          {v.cancellationReason || 'No reason given'}
        </div>
      )}
    </Card>
  )
}
