'use client'
import { useEffect, useState, useRef } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import Image from 'next/image'
import { Building2, MapPin, Calendar, ShieldCheck, Trash2, ArrowLeft, LogIn, Landmark, FileCheck, Wallet, Lock, Check, X, AlertTriangle, UserSearch, ChevronLeft, ChevronRight } from 'lucide-react'
import { propertyApi, viewingApi, reviewApi, verifApi, paymentApi } from '@/lib/api'
import ReportButton from '@/components/report/ReportButton'
import { usePlatformConfig } from '@/hooks/usePlatformConfig'
import { useAuthStore } from '@/lib/store'
import type { PropertyResponse, ReviewResponse, SellerRatingResponse, TrustStatusResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import PropertyLocationMap from '@/components/property/PropertyLocationMap'
import Button from '@/components/ui/Button'
import { StatusBadge, TrustBadge, RatingStars } from '@/components/ui/Badge'
import { Modal, ConfirmModal, PageLoader } from '@/components/ui/Modal'
import Input from '@/components/ui/Input'
import Textarea from '@/components/ui/Textarea'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function PropertyDetailClient() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuthStore()
  const router = useRouter()
  const platformConfig = usePlatformConfig()
  const [prop, setProp]       = useState<PropertyResponse | null>(null)
  const [notFound, setNF]     = useState(false)
  const [reviews, setReviews] = useState<ReviewResponse[]>([])
  const [rating, setRating]   = useState<SellerRatingResponse | null>(null)
  const [trust, setTrust]     = useState<TrustStatusResponse | null>(null)
  const [loading, setLoad]    = useState(true)
  const [viewingModal, setVM] = useState(false)
  const [payModal, setPM]     = useState(false)
  const [viewingAck, setViewingAck] = useState(false)
  const [payAck, setPayAck]         = useState(false)
  const [paying, setPaying]   = useState(false)
  const [payForm, setPF]      = useState({ paymentType: 'FULL_PAYMENT', amount: '', phoneNumber: '' })
  const [deleteModal, setDM]  = useState(false)
  const [deleting, setDel]    = useState(false)
  const [scheduling, setSched]= useState(false)
  const [viewForm, setVF]     = useState({ scheduledAt: '', buyerPhone: '', notes: '' })
  const [activeImage, setActiveImage] = useState(0)
  const galleryRef = useRef<HTMLDivElement>(null)

  const scrollToImage = (i: number) => {
    if (!galleryRef.current || !prop?.imageUrls) return
    const clamped = Math.max(0, Math.min(i, prop.imageUrls.length - 1))
    galleryRef.current.scrollTo({ left: clamped * galleryRef.current.clientWidth, behavior: 'smooth' })
    setActiveImage(clamped)
  }

  const handleGalleryScroll = () => {
    if (!galleryRef.current) return
    const i = Math.round(galleryRef.current.scrollLeft / galleryRef.current.clientWidth)
    setActiveImage(i)
  }

  useEffect(() => {
    if (!id) return
    Promise.allSettled([propertyApi.getById(id), reviewApi.byProperty(id)]).then(([p, r]) => {
      if (p.status === 'fulfilled') {
        setProp(p.value)
        reviewApi.sellerRating(p.value.sellerId).then(setRating).catch(() => {})
        verifApi.trustStatus(p.value.sellerId).then(setTrust).catch(() => {})
      } else setNF(true)
      if (r.status === 'fulfilled') setReviews(r.value.content || [])
    }).finally(() => setLoad(false))
  }, [id])

  const handleDelete = async () => {
    if (!prop) return
    setDel(true)
    try { await propertyApi.delete(prop.id); toast.success('Listing deleted'); router.push('/properties') }
    catch (e: any) { toast.error(e.response?.data?.error || 'Delete failed') }
    finally { setDel(false) }
  }

  const handleSchedule = async () => {
    if (!prop || !user) return
    setSched(true)
    try {
      await viewingApi.schedule({ propertyId: prop.id, sellerId: prop.sellerId, ...viewForm, scheduledAt: new Date(viewForm.scheduledAt).toISOString() })
      toast.success(`Viewing scheduled! Check your M-Pesa for the KES ${platformConfig.viewingFeeKes} fee.`)
      setVM(false)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to schedule viewing') }
    finally { setSched(false) }
  }

  const openPayModal = () => {
    if (!prop) return
    setPF({ paymentType: 'FULL_PAYMENT', amount: String(prop.price), phoneNumber: '' })
    setPM(true)
  }

  const handlePay = async () => {
    if (!prop || !user) return
    const amount = Number(payForm.amount)
    if (!amount || amount <= 0) { toast.error('Enter a valid amount'); return }
    if (!payForm.phoneNumber.trim()) { toast.error('Enter your M-Pesa phone number'); return }
    setPaying(true)
    try {
      const res = await paymentApi.initiate({
        propertyId: prop.id, sellerId: prop.sellerId, amount,
        phoneNumber: payForm.phoneNumber, paymentType: payForm.paymentType,
      })
      toast.success('STK push sent. Check your phone to enter your M-Pesa PIN.')
      setPM(false)
      router.push(`/payments/${res.id}`)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to initiate payment') }
    finally { setPaying(false) }
  }

  const requireLogin = (next: () => void) => {
    if (!user) { toast('Log in to continue', { icon: <Lock size={16}/> }); router.push(`/login?returnTo=/properties/${id}`); return }
    next()
  }

  if (loading) return <PageLoader/>
  if (notFound || !prop) {
    return (
      <div className="max-w-lg mx-auto text-center py-24 px-4">
        <Building2 size={36} className="text-gold-300 mx-auto mb-4"/>
        <h1 className="font-display text-xl font-bold text-gray-900 dark:text-white mb-2">Listing not found</h1>
        <p className="text-sm text-muted mb-6">It may have been sold, rented, or removed by the seller.</p>
        <Link href="/properties"><Button variant="secondary">Browse other listings</Button></Link>
      </div>
    )
  }

  const isSeller = user?.userId === prop.sellerId

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6 space-y-6">
      <Link href="/properties" className="inline-flex items-center gap-2 text-sm text-muted hover:text-gray-900 dark:hover:text-white transition-colors">
        <ArrowLeft size={15}/> Back to listings
      </Link>

      <div className="relative">
        <div
          ref={galleryRef}
          onScroll={handleGalleryScroll}
          className="h-64 sm:h-80 rounded-2xl overflow-x-auto flex snap-x snap-mandatory scroll-smooth no-scrollbar"
        >
          {(prop.imageUrls && prop.imageUrls.length > 0 ? prop.imageUrls : [null]).map((url, i) => (
            <div key={i} className="w-full h-full shrink-0 snap-center relative bg-gradient-to-br from-gold-100 to-amber-50 dark:from-gold-500/10 dark:to-amber-500/5 flex items-center justify-center">
              {url ? (
                <Image src={url} alt={`${prop.title} photo ${i + 1}`} fill priority={i === 0} sizes="(max-width: 640px) 100vw, 800px" className="object-cover"/>
              ) : <Building2 size={56} className="text-gold-300"/>}
            </div>
          ))}
        </div>
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent pointer-events-none rounded-2xl"/>
        <div className="absolute bottom-4 left-4 flex gap-2">
          <StatusBadge status={prop.status}/>
          <TrustBadge identityVerified={prop.sellerIdentityVerified} ownershipVerified={prop.propertyOwnershipVerified} fullyTrusted={prop.fullyTrusted}/>
        </div>
        {isSeller && (
          <div className="absolute top-4 right-4">
            <Button size="sm" variant="secondary" onClick={() => setDM(true)} leftIcon={<Trash2 size={13}/>}>Delete</Button>
          </div>
        )}
        {prop.imageUrls && prop.imageUrls.length > 1 && (
          <>
            <button onClick={() => scrollToImage(activeImage - 1)} disabled={activeImage === 0}
              className="absolute left-2 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-white/90 dark:bg-black/60 flex items-center justify-center text-gray-700 dark:text-white disabled:opacity-0 transition-opacity" aria-label="Previous photo">
              <ChevronLeft size={16}/>
            </button>
            <button onClick={() => scrollToImage(activeImage + 1)} disabled={activeImage === prop.imageUrls.length - 1}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 rounded-full bg-white/90 dark:bg-black/60 flex items-center justify-center text-gray-700 dark:text-white disabled:opacity-0 transition-opacity" aria-label="Next photo">
              <ChevronRight size={16}/>
            </button>
            <div className="absolute bottom-4 right-4 px-2 py-0.5 rounded-md bg-black/50 text-white text-[11px] font-medium">{activeImage + 1} / {prop.imageUrls.length}</div>
          </>
        )}
      </div>

      {prop.imageUrls && prop.imageUrls.length > 1 && (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {prop.imageUrls.map((url, i) => (
            <button key={url} onClick={() => scrollToImage(i)}
              className={`shrink-0 w-16 h-16 rounded-lg overflow-hidden border-2 relative transition-colors ${activeImage === i ? 'border-gold-500' : 'border-transparent opacity-70 hover:opacity-100'}`}>
              <Image src={url} alt={`${prop.title} thumbnail ${i + 1}`} fill sizes="64px" className="object-cover"/>
            </button>
          ))}
        </div>
      )}

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div>
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <h1 className="font-display text-2xl font-bold text-gray-900 dark:text-white">{prop.title}</h1>
              <span className="font-display text-2xl font-bold text-gold-500 shrink-0">{fmt.currency(prop.price)}{prop.listingType === 'RENT' && <span className="text-sm font-normal text-muted">/mo</span>}</span>
            </div>
            <div className="flex items-center justify-between mt-1">
              <p className="flex items-center gap-1 text-muted text-sm"><MapPin size={13}/>{prop.city || prop.subCounty || prop.county}, {prop.county}</p>
              {!isSeller && <ReportButton targetType="LISTING" targetId={prop.id} label="Report this listing"/>}
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            {[
              { label: 'Type', value: prop.propertyType },
              { label: 'Listing', value: prop.listingType === 'SALE' ? 'For Sale' : 'To Rent' },
              prop.bedrooms && { label: 'Bedrooms', value: prop.bedrooms },
              prop.bathrooms && { label: 'Bathrooms', value: prop.bathrooms },
              prop.areaSqm && { label: 'Area', value: `${prop.areaSqm} m²` },
              prop.yearBuilt && { label: 'Built', value: prop.yearBuilt },
              { label: 'Views', value: prop.viewCount },
            ].filter(Boolean).map((s: any) => (
              <div key={s.label} className="bg-gray-50 dark:bg-[#1A1A35] rounded-xl px-4 py-2.5 text-center min-w-[76px]">
                <p className="text-[11px] text-muted mb-0.5">{s.label}</p>
                <p className="font-display font-semibold text-gray-900 dark:text-white text-[13px]">{s.value}</p>
              </div>
            ))}
          </div>

          {prop.description && (
            <Card>
              <h2 className="font-display font-semibold mb-3 text-[15px]">About this property</h2>
              <p className="text-[13px] text-gray-600 dark:text-gray-300 leading-relaxed whitespace-pre-line">{prop.description}</p>
            </Card>
          )}

          <Card>
            <h2 className="font-display font-semibold mb-3 text-[15px] flex items-center gap-1.5"><MapPin size={15} className="text-gold-500"/>Location</h2>
            <PropertyLocationMap latitude={prop.latitude} longitude={prop.longitude} title={prop.title} fallbackLabel={`${prop.city || prop.subCounty || prop.county}, ${prop.county}`} heightClass="h-56"/>
          </Card>

          <Card>
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display font-semibold text-[15px]">Reviews</h2>
              {rating && rating.reviewCount > 0 && (
                <div className="flex items-center gap-2">
                  <RatingStars rating={rating.averageRating}/>
                  <span className="text-sm font-semibold">{rating.averageRating.toFixed(1)}</span>
                  <span className="text-xs text-muted">({rating.reviewCount})</span>
                </div>
              )}
            </div>
            {reviews.length === 0 ? (
              <p className="text-sm text-muted text-center py-6">No reviews yet for this property.</p>
            ) : (
              <div className="space-y-4">
                {reviews.map(r => (
                  <div key={r.id} className="border-b border-base last:border-0 pb-4 last:pb-0">
                    <div className="flex items-center justify-between gap-2 mb-1.5">
                      <div className="flex items-center gap-2">
                        <RatingStars rating={r.rating}/>
                        <span className="text-xs text-muted">{fmt.ago(r.createdAt)}</span>
                      </div>
                      {!isSeller && <ReportButton targetType="REVIEW" targetId={r.id} defaultReason="FAKE_REVIEW"/>}
                    </div>
                    {r.comment && <p className="text-sm text-gray-600 dark:text-gray-300">{r.comment}</p>}
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <div className="space-y-4">
          {trust && (
            <Card>
              <h3 className="font-display font-semibold mb-3 flex items-center gap-2 text-[14px]"><ShieldCheck size={15} className="text-gold-500"/>Seller trust</h3>
              <div className="space-y-2.5 text-[13px]">
                <div className="flex items-center justify-between">
                  <span className="text-muted flex items-center gap-1.5"><FileCheck size={12}/>Identity</span>
                  <span className={`flex items-center gap-1 font-medium ${trust.identityVerified ? 'text-emerald-600' : 'text-red-500'}`}>
                    {trust.identityVerified ? <Check size={13}/> : <X size={13}/>}{trust.identityVerified ? 'Verified' : 'Not verified'}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted flex items-center gap-1.5"><Landmark size={12}/>Land title</span>
                  <span className={`flex items-center gap-1 font-medium ${trust.ownershipVerified ? 'text-emerald-600' : 'text-amber-600'}`}>
                    {trust.ownershipVerified ? <Check size={13}/> : <AlertTriangle size={13}/>}{trust.ownershipVerified ? 'Verified' : 'Pending'}
                  </span>
                </div>
                {trust.identityScore !== undefined && (
                  <div className="flex items-center justify-between"><span className="text-muted">Trust score</span><span className="font-semibold">{trust.identityScore}/100</span></div>
                )}
              </div>
              <Link href={`/sellers/${prop.sellerId}?propertyId=${prop.id}`} className="mt-3 flex items-center justify-center gap-1.5 h-8 rounded-md border border-gold-300 dark:border-gold-500/30 text-gold-600 dark:text-gold-400 text-[12px] font-medium hover:bg-gold-50 dark:hover:bg-gold-500/10 transition-colors">
                <UserSearch size={13}/>View the seller&apos;s full profile
              </Link>
            </Card>
          )}

          {!isSeller && (
            <Card>
              <h3 className="font-display font-semibold mb-1 text-[14px]">Ready to buy?</h3>
              <p className="text-[12px] text-muted mb-4">Pay via M-Pesa. Funds sit in escrow until the deal is confirmed. The seller is never paid directly.</p>
              {user ? (
                user.role === 'BUYER' ? (
                  <Button fullWidth onClick={openPayModal} leftIcon={<Wallet size={15}/>}>Pay {fmt.currency(prop.price)}</Button>
                ) : (
                  <p className="text-[12px] text-muted">Sign in with a buyer account to pay for this property.</p>
                )
              ) : (
                <Button fullWidth onClick={() => requireLogin(openPayModal)} leftIcon={<LogIn size={15}/>}>Log in to pay</Button>
              )}
            </Card>
          )}

          {!isSeller && (
            <Card>
              <h3 className="font-display font-semibold mb-1 text-[14px]">Interested?</h3>
              <p className="text-[12px] text-muted mb-4">Schedule a viewing. A KES {platformConfig.viewingFeeKes} M-Pesa fee confirms your slot.</p>
              {user ? (
                user.role === 'BUYER' ? (
                  <Button fullWidth variant="secondary" onClick={() => setVM(true)} leftIcon={<Calendar size={15}/>}>Schedule viewing</Button>
                ) : (
                  <p className="text-[12px] text-muted">Sign in with a buyer account to schedule a viewing.</p>
                )
              ) : (
                <Button fullWidth variant="secondary" onClick={() => requireLogin(() => setVM(true))} leftIcon={<LogIn size={15}/>}>Log in to schedule</Button>
              )}
            </Card>
          )}

          <Card>
            <div className="space-y-2.5 text-[13px]">
              <div className="flex justify-between"><span className="text-muted">Listed</span><span>{fmt.date(prop.createdAt)}</span></div>
              <div className="flex justify-between"><span className="text-muted">Updated</span><span>{fmt.date(prop.updatedAt)}</span></div>
              <div className="flex justify-between"><span className="text-muted">Status</span><StatusBadge status={prop.status} size="sm"/></div>
            </div>
          </Card>
        </div>
      </div>

      <Modal open={viewingModal} onClose={() => setVM(false)} title="Schedule a viewing"
        footer={<><Button variant="secondary" onClick={() => setVM(false)}>Cancel</Button><Button onClick={handleSchedule} loading={scheduling} disabled={!prop.fullyTrusted && !viewingAck} leftIcon={<Calendar size={14}/>}>Schedule & Pay KES {platformConfig.viewingFeeKes}</Button></>}>
        <div className="space-y-4">
          <div className="flex items-center gap-3 text-[12px] p-2.5 rounded-lg bg-gray-50 dark:bg-white/5">
            <span className={`flex items-center gap-1 ${prop.sellerIdentityVerified ? 'text-emerald-600' : 'text-red-500'}`}>{prop.sellerIdentityVerified ? <Check size={12}/> : <X size={12}/>}Identity</span>
            <span className={`flex items-center gap-1 ${prop.propertyOwnershipVerified ? 'text-emerald-600' : 'text-amber-500'}`}>{prop.propertyOwnershipVerified ? <Check size={12}/> : <AlertTriangle size={12}/>}Title</span>
          </div>
          <p className="text-sm text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3">A KES {platformConfig.viewingFeeKes} viewing fee will be sent to your M-Pesa. Pay this to confirm the viewing.</p>
          {!prop.fullyTrusted && (
            <label className="flex items-start gap-2 text-[12px] text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-500/10 rounded-lg p-3 cursor-pointer">
              <input type="checkbox" className="mt-0.5" checked={viewingAck} onChange={e => setViewingAck(e.target.checked)}/>
              This listing is not fully verified. I want to schedule a viewing anyway.
            </label>
          )}
          <Input label="Preferred date & time" type="datetime-local" required value={viewForm.scheduledAt} onChange={e => setVF(f => ({ ...f, scheduledAt: e.target.value }))}/>
          <Input label="M-Pesa phone number" placeholder="254708374149" required value={viewForm.buyerPhone} onChange={e => setVF(f => ({ ...f, buyerPhone: e.target.value }))}/>
          <Textarea label="Notes for seller" placeholder="Any questions or specific things you want to see?" value={viewForm.notes} onChange={e => setVF(f => ({ ...f, notes: e.target.value }))}/>
        </div>
      </Modal>

      <Modal open={payModal} onClose={() => setPM(false)} title="Pay for this property"
        footer={<><Button variant="secondary" onClick={() => setPM(false)}>Cancel</Button><Button onClick={handlePay} loading={paying} disabled={!prop.fullyTrusted && !payAck} leftIcon={<Wallet size={14}/>}>Send STK Push</Button></>}>
        <div className="space-y-4">
          <div className="flex items-center gap-3 text-[12px] p-2.5 rounded-lg bg-gray-50 dark:bg-white/5">
            <span className={`flex items-center gap-1 ${prop.sellerIdentityVerified ? 'text-emerald-600' : 'text-red-500'}`}>{prop.sellerIdentityVerified ? <Check size={12}/> : <X size={12}/>}Identity</span>
            <span className={`flex items-center gap-1 ${prop.propertyOwnershipVerified ? 'text-emerald-600' : 'text-amber-500'}`}>{prop.propertyOwnershipVerified ? <Check size={12}/> : <AlertTriangle size={12}/>}Title</span>
          </div>
          <p className="text-sm text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 rounded-lg p-3">
            An M-Pesa prompt will be sent to your phone. Funds are held in escrow, and the seller only gets paid after the deal is confirmed by an admin.
          </p>
          <div className="flex items-center justify-between gap-2 text-[12px] text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-500/10 rounded-lg p-3">
            <span>Never pay a seller directly outside this button - only escrow payments made here are protected.</span>
            <ReportButton targetType="USER" targetId={prop.sellerId} label="Report" defaultReason="OFF_PLATFORM_PAYMENT_REQUEST" className="shrink-0"/>
          </div>
          {!prop.fullyTrusted && (
            <label className="flex items-start gap-2 text-[12px] text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-500/10 rounded-lg p-3 cursor-pointer">
              <input type="checkbox" className="mt-0.5" checked={payAck} onChange={e => setPayAck(e.target.checked)}/>
              This listing is not fully verified. I want to proceed with payment anyway.
            </label>
          )}
          <div>
            <label className="text-xs font-medium text-gray-600 dark:text-gray-400 block mb-2">Payment type</label>
            <div className="grid grid-cols-2 gap-2">
              {(['FULL_PAYMENT', 'DEPOSIT'] as const).map(t => (
                <button key={t} type="button" onClick={() => setPF(f => ({ ...f, paymentType: t, amount: t === 'FULL_PAYMENT' ? String(prop.price) : f.amount }))}
                  className={`border-2 rounded-lg p-2.5 text-center text-[13px] font-medium transition-all ${payForm.paymentType === t ? 'border-gold-500 bg-gold-50 dark:bg-gold-500/10 text-gold-600 dark:text-gold-400' : 'border-gray-200 dark:border-[#1E1E3A] text-gray-600 dark:text-gray-300 hover:border-gold-300'}`}>
                  {t === 'FULL_PAYMENT' ? 'Full payment' : 'Deposit'}
                </button>
              ))}
            </div>
          </div>
          <Input label="Amount (KES)" type="number" required value={payForm.amount}
            onChange={e => setPF(f => ({ ...f, amount: e.target.value }))}
            hint={payForm.paymentType === 'FULL_PAYMENT' ? `Listed price: ${fmt.currency(prop.price)}` : 'Enter your deposit amount'}/>
          <Input label="M-Pesa phone number" placeholder="254708374149" required value={payForm.phoneNumber}
            onChange={e => setPF(f => ({ ...f, phoneNumber: e.target.value }))}/>
        </div>
      </Modal>

      <ConfirmModal open={deleteModal} onClose={() => setDM(false)} onConfirm={handleDelete} loading={deleting}
        title="Delete listing" message={`Are you sure you want to delete "${prop.title}"? This cannot be undone.`} label="Delete listing"/>
    </div>
  )
}
