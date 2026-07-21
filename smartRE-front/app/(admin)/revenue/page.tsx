'use client'
import { useEffect, useState } from 'react'
import { BarChart3, DollarSign, TrendingUp, CreditCard, Unlock, ShieldAlert, XCircle, Clock } from 'lucide-react'
import { revenueApi, propertyApi, paymentApi } from '@/lib/api'
import type { RevenueSummaryResponse, RevenueResponse, PropertyResponse, PaymentResponse } from '@/types'
import { usePlatformConfig } from '@/hooks/usePlatformConfig'
import { StatCard } from '@/components/ui/Card'
import { Card } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/Badge'
import { Modal, PageLoader } from '@/components/ui/Modal'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'

export default function RevenuePage() {
  const platformConfig = usePlatformConfig()
  const [summary, setSummary] = useState<RevenueSummaryResponse|null>(null)
  const [items, setItems]     = useState<RevenueResponse[]>([])
  const [pending, setPending] = useState<PaymentResponse[]>([])
  const [pendingLoading, setPendingLoading] = useState(true)
  const [loading, setLoad]    = useState(true)
  const [modal, setModal]     = useState<RevenueResponse|null>(null)
  const [releasing, setRel]   = useState(false)
  const [refundTarget, setRefundTarget] = useState<PaymentResponse|null>(null)
  const [refundReason, setRefundReason] = useState('')
  const [refunding, setRefunding] = useState(false)
  const [form, setForm]       = useState({ payoutMethod:'MPESA_B2C', sellerPhone:'', paybillNumber:'', tillNumber:'', accountNumber:'', bankAccountNumber:'', bankName:'', bankBranch:'', accountName:'', notes:'' })
  const [dealProperty, setDealProperty] = useState<PropertyResponse | null>(null)
  const [overrideAck, setOverrideAck] = useState(false)

  const openRelease = (item: RevenueResponse) => {
    setModal(item)
    setForm({ payoutMethod:'MPESA_B2C', sellerPhone:'', paybillNumber:'', tillNumber:'', accountNumber:'', bankAccountNumber:'', bankName:'', bankBranch:'', accountName:'', notes:'' })
  }

  const openReleaseFromPayment = (p: PaymentResponse) => {
    const feePct = platformConfig.transactionCommissionPct
    const fee = Math.round(p.amount * feePct) / 100
    openRelease({
      id: p.id, paymentId: p.id, buyerId: p.buyerId, sellerId: p.sellerId, propertyId: p.propertyId,
      revenueType: 'TRANSACTION_COMMISSION', grossAmount: p.amount, platformFee: fee,
      sellerPayout: p.amount - fee, feePercentage: feePct, currency: 'KES', status: 'PENDING',
    } as RevenueResponse)
  }

  const submitRefund = async () => {
    if (!refundTarget) return
    if (!refundReason.trim()) { toast.error('A reason is required'); return }
    setRefunding(true)
    try {
      await revenueApi.refund(refundTarget.id, refundReason)
      toast.success('Refund initiated - buyer will receive their money back via M-Pesa')
      setRefundTarget(null); setRefundReason('')
      loadPending()
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Refund failed')
    } finally { setRefunding(false) }
  }

  const loadPending = () => {
    setPendingLoading(true)
    paymentApi.pendingRelease().then(r => setPending(r.content || [])).finally(() => setPendingLoading(false))
  }

  useEffect(() => {
    if (!modal) { setDealProperty(null); setOverrideAck(false); return }
    propertyApi.getById(modal.propertyId).then(setDealProperty).catch(() => setDealProperty(null))
  }, [modal])

  useEffect(() => {
    Promise.allSettled([revenueApi.summary(), revenueApi.all()]).then(([s,i]) => {
      if (s.status==='fulfilled') setSummary(s.value)
      if (i.status==='fulfilled') setItems(i.value.content || [])
    }).finally(()=>setLoad(false))
    loadPending()
  }, [])

  const release = async () => {
    if (!modal) return
    setRel(true)
    try {
      await revenueApi.release(modal.paymentId, form)
      toast.success('Escrow released and payout initiated')
      setModal(null)
      const [s,i] = await Promise.all([revenueApi.summary(), revenueApi.all()])
      setSummary(s); setItems(i.content || [])
      loadPending()
    } catch (e:any) { toast.error(e.response?.data?.error || 'Release failed') }
    finally { setRel(false) }
  }

  if (loading) return <PageLoader/>

  const chartData = [
    { name:'Commissions', value: summary?.commissionRevenue || 0 },
    { name:'Viewing Fees', value: summary?.viewingFeeRevenue || 0 },
    { name:'This Month',   value: summary?.thisMonthRevenue || 0 },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Revenue</h1>
        <p className="text-muted text-sm mt-1">Platform earnings and escrow management</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Total revenue" value={fmt.currency(summary?.totalPlatformFees||0)} icon={<DollarSign size={20}/>} color="gold"/>
        <StatCard label="This month" value={fmt.currency(summary?.thisMonthRevenue||0)} icon={<TrendingUp size={20}/>} color="emerald"/>
        <StatCard label="Commissions" value={fmt.currency(summary?.commissionRevenue||0)} icon={<BarChart3 size={20}/>} color="blue"/>
        <StatCard label="Viewing fees" value={fmt.currency(summary?.viewingFeeRevenue||0)} icon={<CreditCard size={20}/>} color="purple"/>
      </div>

      <Card>
        <h2 className="font-display font-semibold mb-4">Revenue breakdown</h2>
        {chartData.every(d => d.value === 0) ? (
          <div className="h-[200px] flex flex-col items-center justify-center text-center">
            <BarChart3 size={24} className="text-gray-300 dark:text-gray-700 mb-2"/>
            <p className="text-[13px] text-muted">No revenue recorded yet</p>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={chartData}>
              <XAxis dataKey="name" tick={{fontSize:12}} axisLine={false} tickLine={false}/>
              <YAxis tick={{fontSize:12}} axisLine={false} tickLine={false} tickFormatter={v=>fmt.currency(v).split(' ')[1]}/>
              <Tooltip formatter={(v:any)=>[fmt.currency(v),'Amount']} contentStyle={{borderRadius:10,border:'1px solid var(--border)',background:'var(--bg)'}}/>
              <Bar dataKey="value" fill="#C9A227" radius={[6,6,0,0]}/>
            </BarChart>
          </ResponsiveContainer>
        )}
      </Card>

      <Card>
        <div className="flex items-center gap-1.5 mb-4">
          <Clock size={15} className="text-amber-500"/>
          <h2 className="font-display font-semibold">Pending escrow release</h2>
          {pending.length > 0 && <span className="badge bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">{pending.length}</span>}
        </div>
        {pendingLoading ? (
          <div className="space-y-2">{[0,1].map(i => <div key={i} className="skeleton h-16 rounded-lg"/>)}</div>
        ) : pending.length === 0 ? (
          <p className="text-muted text-sm text-center py-8">No completed deals awaiting escrow release</p>
        ) : (
          <div className="space-y-3">
            {pending.map(p => (
              <div key={p.id} className="flex items-center gap-4 p-3 rounded-xl bg-amber-50/50 dark:bg-amber-500/5 border border-amber-100 dark:border-amber-500/20 flex-wrap">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className="text-sm font-semibold text-gray-900 dark:text-white">{p.paymentType.replace(/_/g,' ')}</p>
                    <StatusBadge status={p.status} size="sm"/>
                  </div>
                  <p className="text-xs text-muted">{fmt.currency(p.amount)} · Paid {fmt.date(p.createdAt)} · {p.mpesaReceiptNumber || 'no receipt'}</p>
                </div>
                <Button size="sm" variant="secondary" leftIcon={<Unlock size={13}/>} onClick={() => openReleaseFromPayment(p)}>Release</Button>
                <Button size="sm" variant="ghost" leftIcon={<XCircle size={13}/>} className="text-red-500 hover:text-red-600" onClick={() => setRefundTarget(p)}>Refund</Button>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <h2 className="font-display font-semibold mb-4">Transactions</h2>
        {items.length===0 ? (
          <p className="text-muted text-sm text-center py-8">No revenue transactions yet</p>
        ) : (
          <div className="space-y-3">
            {items.map(item => (
              <div key={item.id} className="flex items-center gap-4 p-3 rounded-xl bg-gray-50 dark:bg-[#1A1A35] flex-wrap">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5">
                    <p className="text-sm font-semibold text-gray-900 dark:text-white">{item.revenueType.replace(/_/g,' ')}</p>
                    <StatusBadge status={item.status} size="sm"/>
                  </div>
                  <p className="text-xs text-muted">Gross: {fmt.currency(item.grossAmount)} · Fee: {fmt.currency(item.platformFee)} · {fmt.date(item.createdAt)}</p>
                </div>
                <div className="text-right">
                  <p className="font-bold text-gray-900 dark:text-white">{fmt.currency(item.sellerPayout)}</p>
                  <p className="text-xs text-muted">seller payout</p>
                </div>
                {!['PAYOUT_COMPLETED'].includes(item.status) && (
                  <Button size="sm" variant="secondary" leftIcon={<Unlock size={13}/>} onClick={()=>openRelease(item)}>
                    Release
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <Modal open={!!modal} onClose={()=>setModal(null)} title="Release escrow" size="sm"
        footer={<><Button variant="secondary" onClick={()=>setModal(null)}>Cancel</Button><Button onClick={release} loading={releasing} leftIcon={<Unlock size={13}/>} disabled={!!dealProperty && !dealProperty.fullyTrusted && !overrideAck}>Release & pay seller</Button></>}>
        {modal && (
          <div className="space-y-4">
            {dealProperty && (
              <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1.5">
                <p className="font-semibold text-gray-900 dark:text-white">{dealProperty.title}</p>
                <p className="text-muted">{dealProperty.county}</p>
                <div className="flex items-center gap-3 pt-1">
                  <span className={dealProperty.sellerIdentityVerified ? 'text-emerald-600' : 'text-red-500'}>Identity: {dealProperty.sellerIdentityVerified ? 'Verified' : 'Not verified'}</span>
                  <span className={dealProperty.propertyOwnershipVerified ? 'text-emerald-600' : 'text-red-500'}>Title: {dealProperty.propertyOwnershipVerified ? 'Verified' : 'Not verified'}</span>
                </div>
              </div>
            )}
            <div className="text-[12px] text-muted grid grid-cols-2 gap-1">
              <span>Buyer: {modal.buyerId.slice(0,8)}...</span>
              <span>Seller: {modal.sellerId.slice(0,8)}...</span>
            </div>
            {dealProperty && !dealProperty.fullyTrusted && (
              <div className="p-3 bg-red-50 dark:bg-red-500/10 rounded-lg space-y-2">
                <p className="text-[13px] text-red-700 dark:text-red-400 flex items-center gap-1.5"><ShieldAlert size={14}/>This listing is not fully verified.</p>
                <label className="flex items-center gap-2 text-[12px] text-red-700 dark:text-red-400 cursor-pointer">
                  <input type="checkbox" checked={overrideAck} onChange={e => setOverrideAck(e.target.checked)}/>
                  I have manually confirmed this deal and want to release escrow anyway
                </label>
              </div>
            )}
            <div className="p-3 bg-gold-50 dark:bg-gold-500/10 rounded-lg text-sm">
              <p className="font-semibold text-gold-700 dark:text-gold-300 mb-1">Payment summary</p>
              <p className="text-gold-600 dark:text-gold-400">Gross: {fmt.currency(modal.grossAmount)}</p>
              <p className="text-gold-600 dark:text-gold-400">Platform ({modal.feePercentage}%): {fmt.currency(modal.platformFee)}</p>
              <p className="font-bold text-emerald-600 mt-1">Seller receives: {fmt.currency(modal.sellerPayout)}</p>
            </div>
            <Select label="Payout method" options={[
              {value:'MPESA_B2C',label:'M-Pesa B2C (phone)'},{value:'MPESA_PAYBILL',label:'Paybill'},
              {value:'MPESA_TILL',label:'Till number'},{value:'BANK_TRANSFER',label:'Bank transfer'},
            ]} value={form.payoutMethod} onChange={e=>setForm(f=>({...f,payoutMethod:e.target.value}))}/>
            {form.payoutMethod==='MPESA_B2C' && (
              <Input label="Seller phone (254...)" placeholder="254712345678" value={form.sellerPhone} onChange={e=>setForm(f=>({...f,sellerPhone:e.target.value}))} required/>
            )}
            {form.payoutMethod==='MPESA_PAYBILL' && (
              <>
                <Input label="Paybill number" placeholder="522522" value={form.paybillNumber} onChange={e=>setForm(f=>({...f,paybillNumber:e.target.value}))} required/>
                <Input label="Account number" placeholder="Seller's paybill account number" value={form.accountNumber} onChange={e=>setForm(f=>({...f,accountNumber:e.target.value}))} required/>
              </>
            )}
            {form.payoutMethod==='MPESA_TILL' && (
              <Input label="Till number" placeholder="123456" value={form.tillNumber} onChange={e=>setForm(f=>({...f,tillNumber:e.target.value}))} required/>
            )}
            {form.payoutMethod==='BANK_TRANSFER' && (
              <>
                <Input label="Account name" placeholder="Seller's account name" value={form.accountName} onChange={e=>setForm(f=>({...f,accountName:e.target.value}))} required/>
                <Input label="Bank account number" value={form.bankAccountNumber} onChange={e=>setForm(f=>({...f,bankAccountNumber:e.target.value}))} required/>
                <Input label="Bank name" placeholder="Equity Bank Kenya" value={form.bankName} onChange={e=>setForm(f=>({...f,bankName:e.target.value}))} required/>
                <Input label="Bank branch" value={form.bankBranch} onChange={e=>setForm(f=>({...f,bankBranch:e.target.value}))}/>
              </>
            )}
            <Input label="Release notes" placeholder="Reason for release..." value={form.notes} onChange={e=>setForm(f=>({...f,notes:e.target.value}))}/>
          </div>
        )}
      </Modal>

      <Modal open={!!refundTarget} onClose={()=>{setRefundTarget(null);setRefundReason('')}} title="Reject &amp; refund buyer" size="sm"
        footer={<><Button variant="secondary" onClick={()=>{setRefundTarget(null);setRefundReason('')}}>Cancel</Button>
          <Button variant="danger" onClick={submitRefund} loading={refunding} leftIcon={<XCircle size={13}/>}>Refund buyer</Button></>}>
        {refundTarget && (
          <div className="space-y-4">
            <div className="p-3 bg-red-50 dark:bg-red-500/10 rounded-lg text-[13px] text-red-700 dark:text-red-400">
              This sends {fmt.currency(refundTarget.amount)} back to the buyer&apos;s M-Pesa number via B2C. This cannot be undone.
            </div>
            <Input label="Reason for refund" required placeholder="e.g. Seller withdrew, dispute resolved in buyer's favor..."
              value={refundReason} onChange={e=>setRefundReason(e.target.value)}/>
          </div>
        )}
      </Modal>
    </div>
  )
}
