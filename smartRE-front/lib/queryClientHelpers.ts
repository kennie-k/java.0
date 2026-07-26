import type { QueryClient, QueryKey } from '@tanstack/react-query'
import type { PageResponse } from '@/types'

export function optimisticRemoveFromPage<T extends { id: string }>(
  qc: QueryClient, key: QueryKey, id: string,
) {
  const previous = qc.getQueryData<PageResponse<T>>(key)
  if (!previous) return previous
  qc.setQueryData<PageResponse<T>>(key, {
    ...previous,
    content: previous.content.filter(item => item.id !== id),
    totalElements: Math.max(0, previous.totalElements - 1),
  })
  return previous
}

export function optimisticPatchInPage<T extends { id: string }>(
  qc: QueryClient, key: QueryKey, id: string, patch: Partial<T>,
) {
  const previous = qc.getQueryData<PageResponse<T>>(key)
  if (!previous) return previous
  qc.setQueryData<PageResponse<T>>(key, {
    ...previous,
    content: previous.content.map(item => (item.id === id ? { ...item, ...patch } : item)),
  })
  return previous
}

export function rollbackPage<T>(qc: QueryClient, key: QueryKey, previous: PageResponse<T> | undefined) {
  if (previous) qc.setQueryData(key, previous)
}
