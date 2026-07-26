import { useMemo } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { propertyApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'

export function useMyListings() {
  const {
    data, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage, refetch,
  } = useInfiniteQuery({
    queryKey: queryKeys.myListings,
    queryFn: ({ pageParam }) => propertyApi.my(pageParam),
    initialPageParam: 0,
    getNextPageParam: lastPage => {
      const next = lastPage.number + 1
      return next < lastPage.totalPages ? next : undefined
    },
  })

  const items = useMemo(() => data?.pages.flatMap(p => p.content ?? []) ?? [], [data])

  return {
    items,
    loading: isLoading,
    loadingMore: isFetchingNextPage,
    hasMore: !!hasNextPage,
    loadMore: fetchNextPage,
    refetch,
    active: items.filter(p => p.status === 'ACTIVE').length,
    inReview: items.filter(p => p.status === 'DRAFT' || p.status === 'PENDING_VERIFICATION').length,
    closed: items.filter(p => ['SOLD', 'RENTED'].includes(p.status)).length,
    totalViews: items.reduce((s, p) => s + (p.viewCount || 0), 0),
  }
}
