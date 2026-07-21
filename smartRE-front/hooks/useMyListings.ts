import { useCallback, useEffect, useState } from 'react'
import { propertyApi } from '@/lib/api'
import type { PropertyResponse } from '@/types'

export function useMyListings() {
  const [items, setItems] = useState<PropertyResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const load = useCallback((p = 0, append = false) => {
    if (append) setLoadingMore(true); else setLoading(true)
    return propertyApi.my(p).then(r => {
      setItems(prev => append ? [...prev, ...(r.content || [])] : (r.content || []))
      setTotalPages(r.totalPages || 1)
      setPage(p)
    }).finally(() => { setLoading(false); setLoadingMore(false) })
  }, [])

  const refetch = useCallback(() => load(0, false), [load])
  const loadMore = useCallback(() => load(page + 1, true), [load, page])

  useEffect(() => { load(0, false) }, [load])

  return {
    items,
    loading,
    loadingMore,
    hasMore: page + 1 < totalPages,
    loadMore,
    refetch,
    active: items.filter(p => p.status === 'ACTIVE').length,
    inReview: items.filter(p => p.status === 'DRAFT' || p.status === 'PENDING_VERIFICATION').length,
    closed: items.filter(p => ['SOLD', 'RENTED'].includes(p.status)).length,
    totalViews: items.reduce((s, p) => s + (p.viewCount || 0), 0),
  }
}
