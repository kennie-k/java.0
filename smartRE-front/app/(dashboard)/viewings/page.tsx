'use client'
import { useEffect, useState } from 'react'
import { Calendar, CheckCircle, XCircle, Clock, Building2 } from 'lucide-react'
import { viewingApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import type { ViewingResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, PageLoader, ConfirmModal } from '@/components/ui/Modal'
import { fmt, cn } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function ViewingsPage() {
  const { user } = useAuthStore()
  const [items, setItems] = useState<ViewingResponse[]>([])
  const [loading, setLoad] = useState(true)
  const [tab, setTab]     = useState<'buyer'|'seller'>(user?.role==='SELLER'?'seller':'buyer')
  const [acting, setAct]  = useState<string|null>(null)
  const [confirm, setConf]= useState<{id:string;action:string}|null>(null)
  const [page, setPage]   = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loadingMore, setLoadingMore] = useState(false)

  const load = async () => {
    setLoad(true)
    try {
      const r = tab==='buyer' ? await viewingApi.myBuyer(0) : await viewingApi.mySeller(0)
      setItems(r.content || [])
      setTotalPages(r.totalPages || 1)
      setPage(0)
    } finally { setLoad(false) }
  }
  useEffect(() => { load() }, [tab])

  const loadMore = async () => {
    setLoadingMore(true)
    try {
      const next = page + 1
      const r = tab==='buyer' ? await viewingApi.myBuyer(next) : await viewingApi.mySeller(next)
      setItems(prev => [...prev, ...(r.content || [])])
      setTotalPages(r.totalPages || 1)
      setPage(next)
    } finally { setLoadingMore(false) }
  }

  const doAction = async (id:string, action:string) => {
    setAct(id+action)
    try {
      if (action==='confirm-seller') await viewingApi.confirmSeller(id)
      else if (action==='confirm-buyer') await viewingApi.confirmBuyer(id)
      else if (action==='complete') await viewingApi.complete(id)
      else if (action==='cancel') await viewingApi.cancel(id, { cancellationReason:'Cancelled by user' })
      toast.success('Done!')
      load()
    } catch (e:any) { toast.error(e.response?.data?.error || 'Action failed') }
    finally { setAct(null); setConf(null) }
  }

  if (loading && items.length===0) return <PageLoader/>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Viewings</h1>
        <p className="text-muted text-sm mt-1">Manage your property viewing appointments</p>
      </div>

      {/* Tabs */}
      {user?.role !== 'SELLER' ? null : (
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

      {loading ? (
        <div className="space-y-4">{[0,1,2].map(i=><div key={i} className="skeleton h-32 rounded-xl"/>)}</div>
      ) : items.length===0 ? (
        <EmptyState icon={<Calendar size={28}/>} title="No viewings" desc="Your viewing appointments will appear here."/>
      ) : (
        <div className="space-y-4">
          {items.map(v => <ViewingCard key={v.id} viewing={v} role={tab} onAction={(id,a) => setConf({id,action:a})} acting={acting}/>)}
        </div>
      )}

      {page + 1 < totalPages && (
        <div className="flex justify-center">
          <Button variant="secondary" size="sm" loading={loadingMore} onClick={loadMore}>Load more</Button>
        </div>
      )}

      <ConfirmModal open={!!confirm} onClose={() => setConf(null)}
        onConfirm={() => confirm && doAction(confirm.id, confirm.action)}
        title={confirm?.action==='cancel' ? 'Cancel viewing' : 'Confirm action'}
        message={confirm?.action==='cancel' ? 'Are you sure you want to cancel this viewing?' : 'Confirm this action?'}
        label={confirm?.action==='cancel'?'Cancel viewing':'Confirm'}
        danger={confirm?.action==='cancel'}
        loading={!!acting}/>
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
          {/* Confirmation status */}
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
    </Card>
  )
}
