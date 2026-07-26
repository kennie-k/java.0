import type { AuthResponse, Role } from '@/types'

type MaybeUser = AuthResponse | null | undefined

export const SELLER_ROLES: Role[] = ['SELLER', 'AGENT']

export const isBuyer = (u: MaybeUser) => u?.role === 'BUYER'
export const isSellerOrAgent = (u: MaybeUser) => !!u && SELLER_ROLES.includes(u.role)
export const isAdmin = (u: MaybeUser) => u?.role === 'ADMIN'
