import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthGuard } from './useAuthGuard'
import { useAuthStore } from '@/lib/store'
import type { AuthResponse } from '@/types'

const replace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
}))

const buyer: AuthResponse = { token: '', userId: '1', fullName: 'Jane', email: 'jane@x.com', role: 'BUYER', verified: true }
const admin: AuthResponse = { token: '', userId: '2', fullName: 'Ada', email: 'ada@x.com', role: 'ADMIN', verified: true }

describe('useAuthGuard', () => {
  beforeEach(() => {
    replace.mockClear()
    useAuthStore.setState({ user: null, hasHydrated: false })
  })

  it('does nothing until the persisted store has hydrated (avoids a false redirect flash)', () => {
    useAuthStore.setState({ user: null, hasHydrated: false })
    const { result } = renderHook(() => useAuthGuard())

    expect(replace).not.toHaveBeenCalled()
    expect(result.current.ready).toBe(false)
  })

  it('redirects to /login once hydrated with no user', () => {
    useAuthStore.setState({ user: null, hasHydrated: true })
    const { result } = renderHook(() => useAuthGuard())

    expect(replace).toHaveBeenCalledWith('/login')
    expect(result.current.ready).toBe(false)
  })

  it('redirects away when the user role is not in allowedRoles', () => {
    useAuthStore.setState({ user: buyer, hasHydrated: true })
    const { result } = renderHook(() => useAuthGuard(['ADMIN'], '/'))

    expect(replace).toHaveBeenCalledWith('/')
    expect(result.current.ready).toBe(false)
  })

  it('does not redirect and reports ready when the role is allowed', () => {
    useAuthStore.setState({ user: admin, hasHydrated: true })
    const { result } = renderHook(() => useAuthGuard(['ADMIN'], '/'))

    expect(replace).not.toHaveBeenCalled()
    expect(result.current.ready).toBe(true)
    expect(result.current.user).toEqual(admin)
  })

  it('is ready with no role restriction as long as a user is present', () => {
    useAuthStore.setState({ user: buyer, hasHydrated: true })
    const { result } = renderHook(() => useAuthGuard())

    expect(replace).not.toHaveBeenCalled()
    expect(result.current.ready).toBe(true)
  })
})
