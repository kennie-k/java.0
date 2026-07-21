import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/lib/store'
import type { Role } from '@/types'

export function useAuthGuard(allowedRoles?: Role[], redirectTo = '/dashboard') {
  const user = useAuthStore(s => s.user)
  const router = useRouter()

  useEffect(() => {
    if (!user) { router.replace('/login'); return }
    if (allowedRoles && !allowedRoles.includes(user.role)) router.replace(redirectTo)
  }, [user, router, allowedRoles?.join(','), redirectTo]) // eslint-disable-line react-hooks/exhaustive-deps

  const ready = !!user && (!allowedRoles || allowedRoles.includes(user.role))
  return { user, ready }
}
