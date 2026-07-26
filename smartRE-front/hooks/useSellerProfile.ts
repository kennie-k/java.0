import { useQueries, useQuery } from '@tanstack/react-query'
import { userApi, verifApi, reviewApi, propertyApi, paymentApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { usePlatformConfig } from './usePlatformConfig'
import { queryKeys } from '@/lib/queryKeys'

export function useSellerProfile(sellerId: string) {
  const { user } = useAuthStore()
  const platformConfig = usePlatformConfig()

  const [sellerQ, trustQ, ratingQ, reviewsQ, listingsQ] = useQueries({
    queries: [
      { queryKey: [...queryKeys.sellerProfile(sellerId), 'profile'], queryFn: () => userApi.getById(sellerId), enabled: !!sellerId, retry: false },
      { queryKey: [...queryKeys.sellerProfile(sellerId), 'trust'], queryFn: () => verifApi.trustStatus(sellerId), enabled: !!sellerId },
      { queryKey: [...queryKeys.sellerProfile(sellerId), 'rating'], queryFn: () => reviewApi.sellerRating(sellerId), enabled: !!sellerId },
      { queryKey: [...queryKeys.sellerProfile(sellerId), 'reviews'], queryFn: () => reviewApi.bySeller(sellerId), enabled: !!sellerId },
      { queryKey: [...queryKeys.sellerProfile(sellerId), 'listings'], queryFn: () => propertyApi.bySeller(sellerId), enabled: !!sellerId },
    ],
  })

  const accessQ = useQuery({
    queryKey: queryKeys.sellerProfileAccess(sellerId),
    queryFn: () => paymentApi.profileAccess(sellerId),
    enabled: !!sellerId && !!user,
  })

  const loading = sellerQ.isLoading || trustQ.isLoading || ratingQ.isLoading || reviewsQ.isLoading || listingsQ.isLoading

  return {
    seller: sellerQ.data ?? null,
    trust: trustQ.data ?? null,
    rating: ratingQ.data ?? null,
    reviews: reviewsQ.data?.content ?? [],
    listings: listingsQ.data ?? [],
    hasAccess: !!user && !!accessQ.data?.hasAccess,
    loading,
    notFound: sellerQ.isError,
    refetchAccess: accessQ.refetch,
    accessFee: platformConfig.profileAccessFeeKes,
  }
}
