import { useCallback, useEffect, useState } from 'react'
import { userApi, verifApi, reviewApi, propertyApi, paymentApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { usePlatformConfig } from './usePlatformConfig'
import type { UserResponse, TrustStatusResponse, SellerRatingResponse, ReviewResponse, PropertyResponse } from '@/types'

export function useSellerProfile(sellerId: string) {
  const { user } = useAuthStore()
  const platformConfig = usePlatformConfig()
  const [seller, setSeller] = useState<UserResponse | null>(null)
  const [trust, setTrust] = useState<TrustStatusResponse | null>(null)
  const [rating, setRating] = useState<SellerRatingResponse | null>(null)
  const [reviews, setReviews] = useState<ReviewResponse[]>([])
  const [listings, setListings] = useState<PropertyResponse[]>([])
  const [hasAccess, setHasAccess] = useState(false)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  const checkAccess = useCallback(() => {
    if (!user) { setHasAccess(false); return }
    paymentApi.profileAccess(sellerId).then(r => setHasAccess(r.hasAccess)).catch(() => setHasAccess(false))
  }, [sellerId, user])

  useEffect(() => {
    if (!sellerId) return
    Promise.allSettled([
      userApi.getById(sellerId),
      verifApi.trustStatus(sellerId),
      reviewApi.sellerRating(sellerId),
      reviewApi.bySeller(sellerId),
      propertyApi.bySeller(sellerId),
    ]).then(([u, t, r, rv, l]) => {
      if (u.status === 'fulfilled') setSeller(u.value); else setNotFound(true)
      if (t.status === 'fulfilled') setTrust(t.value)
      if (r.status === 'fulfilled') setRating(r.value)
      if (rv.status === 'fulfilled') setReviews(rv.value.content || [])
      if (l.status === 'fulfilled') setListings(l.value)
    }).finally(() => setLoading(false))
    checkAccess()
  }, [sellerId]) // eslint-disable-line react-hooks/exhaustive-deps

  return {
    seller, trust, rating, reviews, listings, hasAccess, loading, notFound,
    refetchAccess: checkAccess,
    accessFee: platformConfig.profileAccessFeeKes,
  }
}
