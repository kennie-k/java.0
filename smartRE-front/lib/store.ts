import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthResponse } from '@/types'

interface AuthStore {
  user: AuthResponse | null
  setUser: (u: AuthResponse | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthStore>()(
  persist(
    set => ({
      user: null,
      setUser: user => set({ user }),
      logout: () => set({ user: null }),
    }),
    { name: 'sre_user', partialize: s => ({ user: s.user }) }
  )
)
