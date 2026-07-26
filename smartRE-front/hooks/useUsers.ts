import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { userApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticPatchInPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { UserResponse } from '@/types'

const USERS_KEY = queryKeys.users

export function useUsers() {
  const qc = useQueryClient()
  const { data, isLoading: loading, error } = useQuery({
    queryKey: USERS_KEY,
    queryFn: () => userApi.allAdmin(),
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
    promotingId: promoteMutation.isPending ? (promoteMutation.variables ?? null) : null,
    promote,
    banningId: banMutation.isPending
      ? (banMutation.variables ?? null)
      : unbanMutation.isPending ? (unbanMutation.variables ?? null) : null,
    ban,
    unban,
    sellers: users.filter(u => u.role === 'SELLER').length,
    agents: users.filter(u => u.role === 'AGENT').length,
    buyers: users.filter(u => u.role === 'BUYER').length,
    admins: users.filter(u => u.role === 'ADMIN').length,
  }
}
