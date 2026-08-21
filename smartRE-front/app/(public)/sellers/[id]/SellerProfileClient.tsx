'use client'
import { useEffect, useState } from 'react'
import { useParams, useSearchParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { ShieldCheck, ShieldX, Phone, Calendar, Building2, Lock, Star, FileCheck, Landmark, ArrowLeft, LogIn, Clock } from 'lucide-react'
import { useSellerProfile } from '@/hooks/useSellerProfile'
import { useAuthStore } from '@/lib/store'
import { paymentApi } from '@/lib/api'
import type { PaymentResponse } from '@/types'
import ReportButton from '@/components/report/ReportButton'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import PropertyCard from '@/components/property/PropertyCard'
import { RatingStars } from '@/components/ui/Badge'
import { Modal, PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function SellerProfileClient() {
  const { id } = useParams<{ id: string }>()
  const sp = useSearchParams()
  const router = useRouter()
  const { user } = useAuthStore()
  const { seller, trust, rating, reviews, listings, hasAccess, loading, notFound, refetchAccess, accessFee } = useSellerProfile(id)

  const [payModal, setPM] = useState(false)
  const [phoneNumber, setPhoneNumber] = useState('')
  const [paying, setPaying] = useState(false)
  const [pendingPayment, setPendingPayment] = useState<PaymentResponse | null>(null)
  const [checkingPending, setCheckingPending] = useState(true)

  const pendingKey = `smartre:pendingProfilePayment:${id}`

  useEffect(() => {
    const storedId = localStorage.getItem(pendingKey)
    if (!storedId) { setCheckingPending(false); return }
    paymentApi.getById(storedId).then(p => {
      if (['PENDING', 'STK_PUSHED'].includes(p.status)) setPendingPayment(p)
      else localStorage.removeItem(pendingKey)
    }).catch(() => localStorage.removeItem(pendingKey))
      .finally(() => setCheckingPending(false))
  }, [id])

  useEffect(() => {
    if (hasAccess) { localStorage.removeItem(pendingKey); setPendingPayment(null) }
  }, [hasAccess])

  const handleUnlock = async () => {
    if (!phoneNumber.trim()) { toast.error('Enter your M-Pesa phone number'); return }
    const propertyId = sp.get('propertyId') || listings[0]?.id
    if (!propertyId) { toast.error('No listing context found for this seller'); return }
    setPaying(true)
    try {
      const res = await paymentApi.initiate({
        propertyId, sellerId: id, amount: accessFee, phoneNumber, paymentType: 'PROFILE_ACCESS',
      })
      toast.success('STK push sent. Enter your PIN, then return here.')
      localStorage.setItem(pendingKey, res.id)
      setPendingPayment(res)
      setPM(false)
      router.push(`/payments/${res.id}?returnTo=${encodeURIComponent(`/sellers/${id}`)}`)
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to start payment')
    } finally {
      setPaying(false)
    }
  }

  if (loading) return <PageLoader/>
  if (notFound || !seller) {
    return (
      <div className="max-w-md mx-auto text-center py-24 px-4">
        <ShieldX size={32} className="text-gray-300 mx-auto mb-4"/>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white mb-2">Seller not found</h1>
        <Link href="/properties"><Button variant="secondary" size="sm">Browse listings</Button></Link>
      </div>
    )
  }

  const initial = seller.fullName.trim().charAt(0).toUpperCase()
  const displayName = hasAccess ? seller.fullName : `${seller.fullName.split(' ')[0]} ${seller.fullName.split(' ')[1]?.charAt(0) || ''}.`

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-6 space-y-5">
      <Link href="#" onClick={() => router.back()} className="inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-gray-900 dark:hover:text-white">
        <ArrowLeft size={13}/>Back
      </Link>

      <Card>
        <div className="flex items-start gap-4">
          <div className="w-14 h-14 rounded-full bg-gold-500 text-white flex items-center justify-center font-bold text-lg shrink-0">{initial}</div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2">
              <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">{displayName}</h1>
              <ReportButton targetType="USER" targetId={id} label="Report this seller" defaultReason="SCAM_AGENT"/>
            </div>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-[12px] text-muted">
              <span className="flex items-center gap-1"><Calendar size={11}/>Member since {fmt.date(seller.createdAt)}</span>
              <span className="flex items-center gap-1"><Building2 size={11}/>{listings.length} active listing{listings.length===1?'':'s'}</span>
              {rating && rating.reviewCount > 0 && (
                <span className="flex items-center gap-1"><RatingStars rating={rating.averageRating}/>{rating.averageRating.toFixed(1)} ({rating.reviewCount})</span>
              )}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2.5 mt-4">
          <div className={`rounded-md border px-3 py-2.5 flex items-center gap-2 ${trust?.identityVerified ? 'border-emerald-200 dark:border-emerald-500/30 bg-emerald-50/50 dark:bg-emerald-500/5' : 'border-red-200 dark:border-red-500/30 bg-red-50/50 dark:bg-red-500/5'}`}>
            {trust?.identityVerified ? <ShieldCheck size={15} className="text-emerald-600 shrink-0"/> : <ShieldX size={15} className="text-red-500 shrink-0"/>}
            <div>
              <p className="text-[11px] font-semibold text-gray-900 dark:text-white">National ID + KRA</p>
              <p className="text-[10px] text-muted">{trust?.identityVerified ? 'Verified' : 'Not verified'}</p>
            </div>
          </div>
          <div className={`rounded-md border px-3 py-2.5 flex items-center gap-2 ${trust?.ownershipVerified ? 'border-emerald-200 dark:border-emerald-500/30 bg-emerald-50/50 dark:bg-emerald-500/5' : 'border-amber-200 dark:border-amber-500/30 bg-amber-50/50 dark:bg-amber-500/5'}`}>
            <Landmark size={15} className={trust?.ownershipVerified ? 'text-emerald-600 shrink-0' : 'text-amber-500 shrink-0'}/>
            <div>
              <p className="text-[11px] font-semibold text-gray-900 dark:text-white">Ardhisasa title check</p>
              <p className="text-[10px] text-muted">{trust?.ownershipVerified ? 'Verified' : 'Pending / per listing'}</p>
            </div>
          </div>
        </div>
      </Card>

      {!hasAccess && pendingPayment && (
        <Card className="text-center">
          <Clock size={22} className="text-amber-500 mx-auto mb-2 animate-pulse"/>
          <h2 className="font-display font-semibold text-gray-900 dark:text-white text-[15px] mb-1">Payment pending</h2>
          <p className="text-[12px] text-muted max-w-sm mx-auto mb-4">
            You already have an M-Pesa STK push for this profile awaiting confirmation. Enter your PIN on your phone, or check the current status below.
          </p>
          <Link href={`/payments/${pendingPayment.id}?returnTo=${encodeURIComponent(`/sellers/${id}`)}`}>
            <Button variant="secondary">Check payment status</Button>
          </Link>
        </Card>
      )}

      {!hasAccess && !pendingPayment && !checkingPending && (
        <Card className="text-center">
          <Lock size={22} className="text-gold-500 mx-auto mb-2"/>
          <h2 className="font-display font-semibold text-gray-900 dark:text-white text-[15px] mb-1">Unlock full seller details</h2>
          <p className="text-[12px] text-muted max-w-sm mx-auto mb-4">
            See {seller.fullName.split(' ')[0]}&apos;s phone number, full listing portfolio, and complete review history for a one-time KES {accessFee} fee. This keeps enquiries to serious buyers.
          </p>
          {user ? (
            user.role === 'BUYER' ? (
              <Button onClick={() => setPM(true)}>Unlock for KES {accessFee}</Button>
            ) : (
              <p className="text-[12px] text-muted">Sign in with a buyer account to unlock seller details.</p>
            )
          ) : (
            <Link href={`/login?returnTo=/sellers/${id}`}><Button leftIcon={<LogIn size={14}/>}>Log in to unlock</Button></Link>
          )}
        </Card>
      )}

      {hasAccess && (
        <Card>
          <h2 className="font-display font-semibold text-[14px] mb-3 flex items-center gap-1.5"><FileCheck size={15} className="text-gold-500"/>Contact details</h2>
          <div className="flex items-center gap-2 text-[13px]">
            <Phone size={13} className="text-muted"/>
            <span className="text-gray-900 dark:text-white font-medium">{seller.phone || 'Not provided'}</span>
          </div>
        </Card>
      )}

      {listings.length > 0 && (
        <div>
          <h2 className="font-display font-semibold text-[15px] text-gray-900 dark:text-white mb-3">Active listings</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {listings.map(p => <PropertyCard key={p.id} property={p}/>)}
          </div>
        </div>
      )}

      <Card>
        <h2 className="font-display font-semibold text-[14px] mb-3">Reviews</h2>
        {reviews.length === 0 ? (
          <p className="text-[12px] text-muted text-center py-6">No reviews yet.</p>
        ) : (
          <div className="space-y-3">
            {(hasAccess ? reviews : reviews.slice(0, 1)).map(r => (
              <div key={r.id} className="border-b border-base last:border-0 pb-3 last:pb-0">
                <div className="flex items-center gap-2 mb-1"><RatingStars rating={r.rating}/><span className="text-[11px] text-muted">{fmt.ago(r.createdAt)}</span></div>
                {r.comment && <p className="text-[12px] text-gray-600 dark:text-gray-300">{r.comment}</p>}
              </div>
            ))}
            {!hasAccess && reviews.length > 1 && (
              <p className="text-[11px] text-muted text-center pt-1">{reviews.length - 1} more review{reviews.length - 1 === 1 ? '' : 's'} hidden. Unlock the full profile to see all</p>
            )}
          </div>
        )}
      </Card>

      <Modal open={payModal} onClose={() => setPM(false)} title="Unlock seller profile"
        footer={<><Button variant="secondary" onClick={() => setPM(false)}>Cancel</Button><Button onClick={handleUnlock} loading={paying}>Send STK Push</Button></>}>
        <div className="space-y-3.5">
          <p className="text-[13px] text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-500/10 rounded-md p-3">
            KES {accessFee} via M-Pesa. Access is granted the moment payment confirms and stays unlocked for this seller going forward.
          </p>
          <p className="text-[12px] text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-500/10 rounded-md p-3">
            Never pay this seller directly outside the app - only payments made through SmartRE&apos;s escrow are protected.
          </p>
          <Input label="M-Pesa phone number" placeholder="254708374149" required value={phoneNumber} onChange={e => setPhoneNumber(e.target.value)}/>
        </div>
      </Modal>
    </div>
  )
}
