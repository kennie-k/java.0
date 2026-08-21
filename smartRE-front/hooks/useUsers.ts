import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { userApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticPatchInPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { UserResponse } from '@/types'

const PAGE_SIZE = 20

export function useUsers() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const USERS_KEY = queryKeys.usersPage(page, PAGE_SIZE)
  const { data, isLoading: loading, error } = useQuery({
    queryKey: USERS_KEY,
    queryFn: () => userApi.allAdmin(page, PAGE_SIZE),
    refetchInterval: 15_000,
  })

  // Role breakdown comes from the dedicated stats endpoint, not the current
  // page's `content` — those counts must reflect all users, not just the
  // ~20 currently loaded into view.
  const { data: roleStats } = useQuery({
    queryKey: queryKeys.userAdminStats,
    queryFn: () => userApi.adminStats(),
    refetchInterval: 15_000,
  })

  const promoteMutation = useMutation({
    mutationFn: (id: string) => userApi.promote(id),
    onMutate: (id: string) => optimisticPatchInPage<UserResponse>(qc, USERS_KEY, id, { role: 'ADMIN' }),
    onError: (_e, _id, previous) => rollbackPage(qc, USERS_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  })

  const banMutation = useMutation({
    mutationFn: (id: string) => userApi.ban(id),
    onMutate: (id: string) => optimisticPatchInPage<UserResponse>(qc, USERS_KEY, id, { active: false }),
    onError: (_e, _id, previous) => rollbackPage(qc, USERS_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  })

  const unbanMutation = useMutation({
    mutationFn: (id: string) => userApi.unban(id),
    onMutate: (id: string) => optimisticPatchInPage<UserResponse>(qc, USERS_KEY, id, { active: true }),
    onError: (_e, _id, previous) => rollbackPage(qc, USERS_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  })

  const promote = async (id: string, name: string) => {
    try {
      await promoteMutation.mutateAsync(id)
      toast.success(`${name} is now an admin`)
      return true
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to promote user')
      return false
    }
  }

  const ban = async (id: string, name: string) => {
    try {
      await banMutation.mutateAsync(id)
      toast.success(`${name} has been banned`)
      return true
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to ban user')
      return false
    }
  }

  const unban = async (id: string, name: string) => {
    try {
      await unbanMutation.mutateAsync(id)
      toast.success(`${name} has been unbanned`)
      return true
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to unban user')
      return false
    }
  }

  const users = data?.content ?? []
  return {
    users,
    loading,
    error: error ? 'Failed to load users' : null,
    page,
    setPage,
    totalPages: data?.totalPages ?? 1,
    totalElements: data?.totalElements ?? 0,
    promotingId: promoteMutation.isPending ? (promoteMutation.variables ?? null) : null,
    promote,
    banningId: banMutation.isPending
      ? (banMutation.variables ?? null)
      : unbanMutation.isPending ? (unbanMutation.variables ?? null) : null,
    ban,
    unban,
    sellers: roleStats?.sellers ?? 0,
    agents: roleStats?.agents ?? 0,
    buyers: roleStats?.buyers ?? 0,
    admins: roleStats?.admins ?? 0,
  }
}
