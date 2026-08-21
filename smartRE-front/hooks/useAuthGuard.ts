import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/lib/store'
import type { Role } from '@/types'

export function useAuthGuard(allowedRoles?: Role[], redirectTo = '/dashboard') {
  const user = useAuthStore(s => s.user)
  const hasHydrated = useAuthStore(s => s.hasHydrated)
  const router = useRouter()

  useEffect(() => {
    if (!hasHydrated) return
    if (!user) { router.replace('/login'); return }
    if (allowedRoles && !allowedRoles.includes(user.role)) router.replace(redirectTo)
  }, [user, hasHydrated, router, allowedRoles?.join(','), redirectTo])

  const ready = hasHydrated && !!user && (!allowedRoles || allowedRoles.includes(user.role))
  return { user, ready }
}
