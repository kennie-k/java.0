import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './store'
import type { AuthResponse } from '@/types'

const sampleUser: AuthResponse = {
  token: 'super-secret-jwt-should-never-be-persisted',
  userId: 'user-1',
  fullName: 'Jane Wanjiru',
  email: 'jane@example.com',
  role: 'BUYER',
  verified: true,
}

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ user: null, hasHydrated: false })
  })

  it('strips the JWT before storing it in memory', () => {
    useAuthStore.getState().setUser(sampleUser)
    expect(useAuthStore.getState().user?.token).toBe('')
    expect(useAuthStore.getState().user?.fullName).toBe('Jane Wanjiru')
  })

  it('never writes the raw JWT into localStorage (persisted under sre_user)', () => {
    useAuthStore.getState().setUser(sampleUser)

    const raw = localStorage.getItem('sre_user')
    expect(raw).toBeTruthy()
    expect(raw).not.toContain(sampleUser.token)

    const persisted = JSON.parse(raw as string)
    expect(persisted.state.user.token).toBe('')
    expect(persisted.state.user.email).toBe(sampleUser.email)
  })

  it('setUser(null) clears the user without throwing', () => {
    useAuthStore.getState().setUser(sampleUser)
    useAuthStore.getState().setUser(null)
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('logout() clears the user', () => {
    useAuthStore.getState().setUser(sampleUser)
    useAuthStore.getState().logout()
    expect(useAuthStore.getState().user).toBeNull()
    const raw = localStorage.getItem('sre_user')
    expect(JSON.parse(raw as string).state.user).toBeNull()
  })

  it('setHasHydrated flips the hydration flag used to gate route guards', () => {
    expect(useAuthStore.getState().hasHydrated).toBe(false)
    useAuthStore.getState().setHasHydrated(true)
    expect(useAuthStore.getState().hasHydrated).toBe(true)
  })
})
