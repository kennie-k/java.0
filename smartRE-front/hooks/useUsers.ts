import { useCallback, useState } from 'react'
import toast from 'react-hot-toast'
import { userApi } from '@/lib/api'
import { useFetch } from './useFetch'

export function useUsers() {
  const { data, loading, refetch } = useFetch(() => userApi.allAdmin(), [])
  const [promotingId, setPromotingId] = useState<string | null>(null)

  const promote = useCallback(async (id: string, name: string) => {
    setPromotingId(id)
    try {
      await userApi.promote(id)
      toast.success(`${name} is now an admin`)
      await refetch()
      return true
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to promote user')
      return false
    } finally {
      setPromotingId(null)
    }
  }, [refetch])

  const users = data?.content ?? []
  return {
    users,
    loading,
    promotingId,
    promote,
    sellers: users.filter(u => u.role === 'SELLER').length,
    buyers: users.filter(u => u.role === 'BUYER').length,
    admins: users.filter(u => u.role === 'ADMIN').length,
  }
}
