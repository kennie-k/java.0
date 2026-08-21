'use client'
import { useRouter } from 'next/navigation'
import { authApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import toast from 'react-hot-toast'

export function useLogout(redirectTo = '/login') {
  const router = useRouter()
  const { logout } = useAuthStore()

  return async () => {
    try { await authApi.logout() } catch {}
    logout()
    toast.success('Logged out')
    router.push(redirectTo)
  }
}
