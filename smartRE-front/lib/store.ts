import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '@/types'

interface AuthStore {
  user: AuthResponse | null
  hasHydrated: boolean
  setUser: (u: AuthResponse | null) => void
  logout: () => void
  setHasHydrated: (v: boolean) => void
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      user: null,
      hasHydrated: false,

      setUser: user => set({ user: user ? { ...user, token: '' } : null }),
      logout: () => set({ user: null }),
      setHasHydrated: v => set({ hasHydrated: v }),
    }),
    {
      name: 'sre_user',
      partialize: s => ({ user: s.user }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true)
      },
    }
  )
)

if (typeof window !== 'undefined') {
  window.addEventListener('storage', e => {
    if (e.key === 'sre_user' || e.key === null) window.location.reload()
  })
}
