'use client'
import { useEffect, useRef } from 'react'
import { useAuthStore } from '@/lib/store'
import { validateSession } from '@/lib/api'

// The persisted `sre_user` in localStorage never expires on its own — it only
// gets cleared on explicit logout or a live 401 from an interactive request.
// This quietly checks it against the real session once per app load so stale
// logins (expired/revoked/banned) don't keep rendering as "logged in" forever.
export default function SessionValidator() {
  const user = useAuthStore(s => s.user)
  const hasHydrated = useAuthStore(s => s.hasHydrated)
  const logout = useAuthStore(s => s.logout)
  const checked = useRef(false)

  useEffect(() => {
    if (!hasHydrated || !user || checked.current) return
    checked.current = true
    validateSession().catch(err => {
      if (err.response?.status === 401 || err.response?.status === 403) logout()
    })
  }, [hasHydrated, user, logout])

  return null
}
