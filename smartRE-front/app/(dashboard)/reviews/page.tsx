'use client'
import { useEffect, useState } from 'react'
import { Star, Plus } from 'lucide-react'
import { reviewApi, paymentApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import type { ReviewResponse, PaymentResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { RatingStars } from '@/components/ui/Badge'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function ReviewsPage() {
  const { user } = useAuthStore()
  const [reviews, setReviews]   = useState<ReviewResponse[]>([])
  const [payments, setPayments] = useState<PaymentResponse[]>([])
  const [loading, setLoad]      = useState(true)
  const [modal, setModal]       = useState(false)
  const [submitting, setSub]    = useState(false)
  const [form, setForm]         = useState({ sellerId:'', propertyId:'', paymentId:'', rating:'5', comment:'' })

  useEffect(() => {
    if (!user) return
    Promise.allSettled([
      user.role === 'BUYER' ? reviewApi.myReviews() : reviewApi.bySeller(user.userId),
      paymentApi.my(),
    ]).then(([r,p]) => {
      if (r.status==='fulfilled') setReviews(r.value.content || [])
      if (p.status==='fulfilled') setPayments((p.value.content || []).filter(x=>x.status==='COMPLETED'))
    }).finally(()=>setLoad(false))
  }, [user])

  const submit = async () => {
    if (!form.paymentId) { toast.error('Select a payment'); return }
    setSub(true)
    try {
      const pay = payments.find(p=>p.id===form.paymentId)
      if (!pay) { toast.error('Payment not found'); return }
      await reviewApi.create({ sellerId:pay.sellerId, propertyId:pay.propertyId, paymentId:pay.id, rating:+form.rating, comment:form.comment })
      toast.success('Review submitted!')
      setModal(false)
      const r = await reviewApi.myReviews(); setReviews(r.content || [])
    } catch (e:any) { toast.error(e.response?.data?.error || 'Review failed') }
    finally { setSub(false) }
  }

  if (loading) return <PageLoader/>

  const avg = reviews.length ? (reviews.reduce((s,r)=>s+r.rating,0)/reviews.length).toFixed(1) : null

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">{user?.role === 'BUYER' ? 'Your reviews' : 'Reviews'}</h1>
          <p className="text-muted text-sm mt-1">{reviews.length} review{reviews.length!==1?'s':''}{avg ? ` · ${avg} avg rating` : ''}</p>
        </div>
        {user?.role==='BUYER' && <Button leftIcon={<Plus size={15}/>} onClick={()=>setModal(true)}>Write review</Button>}
      </div>

      {avg && (
        <Card>
          <div className="flex items-center gap-4">
            <p className="font-display text-5xl font-bold text-gray-900 dark:text-white">{avg}</p>
            <div>
              <RatingStars rating={+avg}/>
              <p className="text-sm text-muted mt-1">{reviews.length} reviews</p>
            </div>
            <div className="flex-1 ml-6 space-y-1.5">
              {[5,4,3,2,1].map(n => {
                const cnt = reviews.filter(r=>r.rating===n).length
                const pct = reviews.length ? (cnt/reviews.length*100) : 0
                return (
                  <div key={n} className="flex items-center gap-2 text-xs">
                    <span className="w-2 text-muted">{n}</span>
                    <div className="flex-1 h-1.5 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                      <div className="h-full bg-amber-400 rounded-full" style={{width:`${pct}%`}}/>
                    </div>
                    <span className="w-6 text-muted text-right">{cnt}</span>
                  </div>
                )
              })}
            </div>
          </div>
        </Card>
      )}

      {reviews.length===0 ? (
        <EmptyState icon={<Star size={28}/>} title="No reviews yet" desc="Reviews from completed property transactions will appear here."/>
      ) : (
        <div className="space-y-4">
          {reviews.map(r => (
            <Card key={r.id}>
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-full bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0 font-bold text-sm">
                  {fmt.initials('Reviewer')}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <RatingStars rating={r.rating}/>
                    <span className="text-xs text-muted">{fmt.ago(r.createdAt)}</span>
                    {r.verified && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 text-xs">Verified purchase</span>}
                  </div>
                  {r.comment && <p className="text-sm text-gray-700 dark:text-gray-300">{r.comment}</p>}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={modal} onClose={()=>setModal(false)} title="Write a review" size="sm"
        footer={<><Button variant="secondary" onClick={()=>setModal(false)}>Cancel</Button><Button onClick={submit} loading={submitting}>Submit review</Button></>}>
        <div className="space-y-4">
          <Select label="Select completed payment" required options={payments.map(p=>({value:p.id,label:`${p.paymentType} · ${fmt.currency(p.amount)} · ${fmt.date(p.createdAt)}`}))}
            value={form.paymentId} onChange={e=>setForm(f=>({...f,paymentId:e.target.value}))}/>
          <div>
            <label className="text-xs font-medium text-gray-600 dark:text-gray-400 block mb-2">Rating</label>
            <div className="flex gap-2">
              {[1,2,3,4,5].map(n => (
                <button key={n} type="button" onClick={()=>setForm(f=>({...f,rating:String(n)}))}
                  className={`transition-transform hover:scale-110 ${+form.rating>=n?'text-amber-400':'text-gray-200 dark:text-gray-700'}`}>
                  <Star size={26} className={+form.rating>=n?'fill-amber-400':''}/>
                </button>
              ))}
            </div>
          </div>
          <Textarea label="Your review" placeholder="Share your experience..." value={form.comment} onChange={e=>setForm(f=>({...f,comment:e.target.value}))}/>
        </div>
      </Modal>
    </div>
  )
}
