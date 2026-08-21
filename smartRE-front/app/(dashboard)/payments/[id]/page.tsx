'use client'
import { useEffect, useState, Suspense } from 'react'
import { useParams, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { ArrowLeft, Receipt, Activity, CheckCircle, Clock, XCircle, ArrowRight } from 'lucide-react'
import { paymentApi } from '@/lib/api'
import type { PaymentResponse, PaymentAuditResponse, PaymentReceiptResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'

export default function PaymentDetailPage() {
  return <Suspense fallback={<PageLoader/>}><PaymentDetail/></Suspense>
}

function PaymentDetail() {
  const { id } = useParams<{id:string}>()
  const sp = useSearchParams()
  const returnTo = sp.get('returnTo')
  const [payment, setPay] = useState<PaymentResponse|null>(null)
  const [audit, setAudit] = useState<PaymentAuditResponse[]>([])
  const [receipt, setReceipt] = useState<PaymentReceiptResponse|null>(null)
  const [loading, setLoad] = useState(true)

  useEffect(() => {
    if (!id) return
    Promise.allSettled([
      paymentApi.getById(id),
      paymentApi.audit(id),
      paymentApi.receipt(id),
    ]).then(([p,a,r]) => {
      if (p.status==='fulfilled') setPay(p.value)
      if (a.status==='fulfilled') setAudit(a.value)
      if (r.status==='fulfilled') setReceipt(r.value)
    }).finally(() => setLoad(false))
  }, [id])

  useEffect(() => {
    if (!id || !payment) return
    if (!['PENDING', 'STK_PUSHED'].includes(payment.status)) return
    const interval = setInterval(() => {
      Promise.allSettled([paymentApi.getById(id), paymentApi.audit(id), paymentApi.receipt(id)]).then(([p, a, r]) => {
        if (p.status === 'fulfilled') setPay(p.value)
        if (a.status === 'fulfilled') setAudit(a.value)
        if (r.status === 'fulfilled') setReceipt(r.value)
      })
    }, 4000)
    return () => clearInterval(interval)
  }, [id, payment?.status])

  if (loading) return <PageLoader/>
  if (!payment) {
    return (
      <div className="max-w-3xl mx-auto space-y-6">
        <Link href="/payments" className="inline-flex items-center gap-2 text-sm text-muted hover:text-gray-900 dark:hover:text-white">
          <ArrowLeft size={15}/>Back to payments
        </Link>
        <EmptyState icon={<Receipt size={28}/>} title="Payment not found"
          desc="It may not exist, or you may not have access to view it."/>
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <Link href="/payments" className="inline-flex items-center gap-2 text-sm text-muted hover:text-gray-900 dark:hover:text-white">
        <ArrowLeft size={15}/>Back to payments
      </Link>

      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-2xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center">
          <Receipt size={24}/>
        </div>
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">{payment.paymentType.replace(/_/g,' ')}</h1>
          <p className="text-muted text-sm">{fmt.datetime(payment.createdAt)}</p>
        </div>
        <div className="ml-auto text-right">
          <p className="font-display text-lg font-semibold text-gray-900 dark:text-white">{fmt.currency(payment.amount, payment.currency)}</p>
          <StatusBadge status={payment.status}/>
        </div>
      </div>

      {['PENDING', 'STK_PUSHED'].includes(payment.status) && (
        <div className="flex items-center gap-2 text-sm text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3">
          <Clock size={15} className="animate-pulse shrink-0"/>
          Waiting for M-Pesa confirmation. Enter your PIN on your phone. This page updates automatically.
        </div>
      )}

      {payment.status === 'COMPLETED' && returnTo && (
        <div className="flex items-center justify-between gap-3 p-3.5 rounded-lg bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/20">
          <p className="text-[13px] text-emerald-700 dark:text-emerald-400 font-medium">Payment confirmed. You can continue now.</p>
          <Link href={returnTo}><Button size="sm" rightIcon={<ArrowRight size={13}/>}>Continue</Button></Link>
        </div>
      )}

      <Card>
        <h2 className="font-display font-semibold text-[14px] mb-4">Payment details</h2>
        <div className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
          {[
            ['Payment ID', payment.id.slice(0,8)+'...'],
            ['Phone number', fmt.phone(payment.phoneNumber)],
            ['Currency', payment.currency],
            ['Escrow', payment.escrowReleased ? 'Released' : 'Held'],
            payment.mpesaCheckoutRequestId && ['Checkout ID', payment.mpesaCheckoutRequestId.slice(0,20)+'...'],
            payment.mpesaReceiptNumber && ['M-Pesa receipt', payment.mpesaReceiptNumber],
          ].filter(Boolean).map((row:any) => (
            <div key={row[0]}><p className="text-muted mb-0.5">{row[0]}</p><p className="font-medium">{row[1]}</p></div>
          ))}
        </div>
      </Card>

      {receipt && (
        <Card>
          <div className="flex items-center gap-2 mb-4">
            <Receipt size={16} className="text-gold-500"/>
            <h2 className="font-display font-semibold text-[14px]">Official receipt</h2>
            <span className="ml-auto font-mono text-sm text-gold-500 font-semibold">{receipt.receiptNumber}</span>
          </div>
          <div className="bg-gray-50 dark:bg-[#1A1A35] rounded-xl p-4 space-y-3">
            <div className="flex justify-between text-sm"><span className="text-muted">Gross amount</span><span className="font-semibold">{fmt.currency(receipt.grossAmount)}</span></div>
            <div className="flex justify-between text-sm"><span className="text-muted">Platform fee ({receipt.grossAmount ? ((receipt.platformFee / receipt.grossAmount) * 100).toFixed(1) : '0'}%)</span><span className="text-gray-600 dark:text-gray-400">{fmt.currency(receipt.platformFee)}</span></div>
            <div className="border-t border-base pt-3 flex justify-between text-sm"><span className="font-semibold">Seller payout</span><span className="font-bold text-emerald-600">{fmt.currency(receipt.sellerPayout)}</span></div>
            <div className="pt-2 text-xs text-muted">M-Pesa: {receipt.mpesaReceipt} · Issued: {fmt.datetime(receipt.issuedAt)}</div>
          </div>
        </Card>
      )}

      {audit.length>0 && (
        <Card>
          <h2 className="font-display font-semibold text-[14px] mb-4 flex items-center gap-2"><Activity size={16} className="text-gold-500"/>Audit trail</h2>
          <div className="relative">
            <div className="absolute left-5 top-0 bottom-0 w-px bg-gray-200 dark:bg-gray-800"/>
            <div className="space-y-4">
              {audit.map((a,i) => (
                <div key={a.id} className="flex gap-4 relative">
                  <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 z-10 ${
                    a.newStatus==='COMPLETED' ? 'bg-emerald-100 dark:bg-emerald-500/15 text-emerald-600' :
                    a.newStatus==='FAILED'    ? 'bg-red-50 dark:bg-red-500/10 text-red-500' :
                    'bg-white dark:bg-[#0F0F20] border-2 border-gray-200 dark:border-gray-700 text-gray-500'
                  }`}>
                    {a.newStatus==='COMPLETED'?<CheckCircle size={18}/>:a.newStatus==='FAILED'?<XCircle size={18}/>:<Clock size={16}/>}
                  </div>
                  <div className="flex-1 pb-4">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="text-sm font-semibold text-gray-900 dark:text-white">{a.eventType.replace(/_/g,' ')}</p>
                      {a.newStatus && <StatusBadge status={a.newStatus} size="sm"/>}
                    </div>
                    <p className="text-xs text-muted mt-0.5">{fmt.datetime(a.createdAt)}</p>
                    {a.detail && <p className="text-xs text-muted mt-1 font-mono break-all">{a.detail}</p>}
                    {a.mpesaReceipt && <p className="text-xs text-emerald-600 mt-1">Receipt: <strong>{a.mpesaReceipt}</strong></p>}
                    {a.actorIp && <p className="text-xs text-muted">IP: {a.actorIp} · Role: {a.actorRole}</p>}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}
