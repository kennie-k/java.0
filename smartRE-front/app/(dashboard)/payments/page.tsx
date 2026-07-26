'use client'
import { useEffect, useState } from 'react'
import Link from 'next/link'
import { CreditCard, Receipt, Eye, CheckCircle, Clock, XCircle } from 'lucide-react'
import { paymentApi } from '@/lib/api'
import type { PaymentResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'

export default function PaymentsPage() {
  const [items, setItems] = useState<PaymentResponse[]>([])
  const [loading, setLoad] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    paymentApi.my(0).then(r => { setItems(r.content || []); setTotalPages(r.totalPages || 1) }).finally(() => setLoad(false))
  }, [])

  const loadMore = () => {
    setLoadingMore(true)
    const next = page + 1
    paymentApi.my(next).then(r => {
      setItems(prev => [...prev, ...(r.content || [])])
      setTotalPages(r.totalPages || 1)
      setPage(next)
    }).finally(() => setLoadingMore(false))
  }

  if (loading) return <PageLoader/>

  const total = items.filter(p=>p.status==='COMPLETED').reduce((s,p)=>s+p.amount,0)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Payments</h1>
        <p className="text-muted text-sm mt-1">Your M-Pesa payment history</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card><p className="text-sm text-muted mb-1">Total paid</p><p className="font-display text-lg font-semibold text-gray-900 dark:text-white">{fmt.currency(total)}</p></Card>
        <Card><p className="text-sm text-muted mb-1">Completed</p><p className="font-display text-lg font-semibold text-emerald-600">{items.filter(p=>p.status==='COMPLETED').length}</p></Card>
        <Card><p className="text-sm text-muted mb-1">Pending</p><p className="font-display text-lg font-semibold text-amber-600">{items.filter(p=>['PENDING','STK_PUSHED'].includes(p.status)).length}</p></Card>
      </div>

      {items.length===0 ? (
        <EmptyState icon={<CreditCard size={28}/>} title="No payments yet" desc="Your M-Pesa payment history will appear here."/>
      ) : (
        <div className="space-y-3">
          {items.map(p => (
            <Card key={p.id} hover>
              <div className="flex items-center gap-4 flex-wrap">
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${
                  p.status==='COMPLETED' ? 'bg-emerald-100 dark:bg-emerald-500/10 text-emerald-600' :
                  p.status==='FAILED'    ? 'bg-red-50 dark:bg-red-500/10 text-red-500' :
                  'bg-amber-50 dark:bg-amber-500/10 text-amber-600'
                }`}>
                  {p.status==='COMPLETED' ? <CheckCircle size={20}/> : p.status==='FAILED' ? <XCircle size={20}/> : <Clock size={20}/>}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className="font-semibold text-sm text-gray-900 dark:text-white">{p.paymentType.replace(/_/g,' ')}</p>
                    <StatusBadge status={p.status} size="sm"/>
                    {p.escrowReleased && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 text-xs">Escrow released</span>}
                  </div>
                  <p className="text-xs text-muted">Phone: {fmt.phone(p.phoneNumber)} · {fmt.datetime(p.createdAt)}</p>
                  {p.mpesaReceiptNumber && <p className="text-xs text-muted">M-Pesa receipt: <strong>{p.mpesaReceiptNumber}</strong></p>}
                  {p.failureReason && <p className="text-xs text-red-500 mt-0.5">{p.failureReason}</p>}
                </div>
                <div className="text-right">
                  <p className="font-display font-bold text-lg text-gray-900 dark:text-white">{fmt.currency(p.amount, p.currency)}</p>
                  <Link href={`/payments/${p.id}`} className="text-xs text-gold-500 hover:text-gold-600 flex items-center gap-1 justify-end mt-1"><Eye size={12}/>View details</Link>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {page + 1 < totalPages && (
        <div className="flex justify-center">
          <Button variant="secondary" size="sm" loading={loadingMore} onClick={loadMore}>Load more</Button>
        </div>
      )}
    </div>
  )
}
