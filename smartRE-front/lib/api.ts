import axios from 'axios'
import toast from 'react-hot-toast'

const BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

const api = axios.create({ baseURL: BASE, timeout: 30000, withCredentials: true })

api.interceptors.response.use(
  r => r,
  err => {
    const url = err.config?.url || ''
    const isAuthEndpoint = url.includes('/api/auth/login') || url.includes('/api/auth/register')
    if (err.response?.status === 401 && typeof window !== 'undefined' && !isAuthEndpoint) {
      const hadSession = !!localStorage.getItem('sre_user')
      localStorage.removeItem('sre_user')
      if (hadSession) {
        toast.error('Your session has expired. Please log in again.')
        const returnTo = window.location.pathname + window.location.search
        setTimeout(() => {
          window.location.href = `/login?returnTo=${encodeURIComponent(returnTo)}`
        }, 1200)
      }
    }
    return Promise.reject(err)
  }
)

const g = <T>(url: string, p?: object) => api.get<T>(url, { params: p }).then(r => r.data)
const po = <T>(url: string, d?: object) => api.post<T>(url, d).then(r => r.data)
const pu = <T>(url: string, d?: object) => api.put<T>(url, d).then(r => r.data)
const puParams = <T>(url: string, p?: object) => api.put<T>(url, undefined, { params: p }).then(r => r.data)
const de = <T>(url: string) => api.delete<T>(url).then(r => r.data)

import type {
  AuthResponse, UserResponse, PropertyResponse, PageResponse,
  IdentityVerificationResponse, TrustStatusResponse,
  ViewingResponse, PaymentResponse, PaymentAuditResponse,
  PaymentReceiptResponse, RevenueSummaryResponse, RevenueResponse,
  ReviewResponse, SellerRatingResponse,
} from '@/types'

// AUTH
export const authApi = {
  login:    (d:{email:string;password:string}) => po<AuthResponse>('/api/auth/login', d),
  register: (d:object) => po<AuthResponse>('/api/auth/register', d),
  logout:   () => po('/api/auth/logout'),
}

// USER
export const userApi = {
  me:       () => g<UserResponse>('/api/users/me'),
  updateMe: (d:object) => pu<UserResponse>('/api/users/me', d),
  changePassword: (d:object) => pu<void>('/api/users/me/password', d),
  getById:  (id:string) => g<UserResponse>(`/api/users/${id}`),
  allAdmin: () => g<PageResponse<UserResponse>>('/api/users/admin/all'),
  promote:  (id:string) => pu<UserResponse>(`/api/users/admin/${id}/promote`),
}

// PROPERTY
export const propertyApi = {
  create:   (d:object) => po<PropertyResponse>('/api/properties', d),
  update:   (id:string, d:object) => pu<PropertyResponse>(`/api/properties/${id}`, d),
  delete:   (id:string) => de(`/api/properties/${id}`),
  getById:  (id:string) => g<PropertyResponse>(`/api/properties/${id}`),
  search:   (p:object) => g<PageResponse<PropertyResponse>>('/api/properties/search', p),
  my:       (page = 0) => g<PageResponse<PropertyResponse>>('/api/properties/my', { page, size: 20 }),
  bySeller: (sid:string) => g<PropertyResponse[]>(`/api/properties/seller/${sid}`),
}

// VERIFICATION
export const verifApi = {
  trustStatus:  (uid:string) => g<TrustStatusResponse>(`/api/verification/trust-status/${uid}`),
  trustProp:    (uid:string, pid:string) => g<TrustStatusResponse>(`/api/verification/trust-status/${uid}/property/${pid}`),
  startId:      () => po<IdentityVerificationResponse>('/api/verification/identity/start'),
  uploadIdDoc:  (d:object) => po('/api/verification/identity/documents', d),
  submitId:     () => po<IdentityVerificationResponse>('/api/verification/identity/submit'),
  myId:         () => g<IdentityVerificationResponse>('/api/verification/identity/me'),
  idAdminQueue: () => g<PageResponse<IdentityVerificationResponse>>('/api/verification/identity/admin/queue'),
  idAdminReview:(id:string, d:object) => pu(`/api/verification/identity/admin/${id}/review`, d),
  startOwner:   (d:object) => po('/api/verification/ownership/start', d),
  uploadOwnerDoc:(id:string, d:object) => po(`/api/verification/ownership/${id}/documents`, d),
  submitOwner:  (id:string) => po(`/api/verification/ownership/${id}/submit`),
  myOwner:      () => g('/api/verification/ownership/me'),
  ownerByProp:  (pid:string) => g(`/api/verification/ownership/property/${pid}`),
  ownerAdminQueue: () => g<PageResponse<any>>('/api/verification/ownership/admin/queue', { status: 'MINISTRY_LANDS_CHECK' }),
  ownerAdminMinistry:(id:string,ministryConfirmed:boolean,notes?:string) => puParams(`/api/verification/ownership/admin/${id}/ministry-check`,{ministryConfirmed,notes}),
  ownerAdminEncumb:(id:string,encumbranceClear:boolean,notes?:string)   => puParams(`/api/verification/ownership/admin/${id}/encumbrance-check`,{encumbranceClear,notes}),
  ownerAdminLegal:(id:string,d:object)    => pu(`/api/verification/ownership/admin/${id}/legal-check`,d),
  ownerAdminFinal:(id:string,d:object)    => pu(`/api/verification/ownership/admin/${id}/final-decision`,d),
}

// VIEWING
export const viewingApi = {
  schedule:      (d:object) => po<ViewingResponse>('/api/viewings', d),
  myBuyer:       (page = 0) => g<PageResponse<ViewingResponse>>('/api/viewings/my/buyer', { page, size: 20 }),
  mySeller:      (page = 0) => g<PageResponse<ViewingResponse>>('/api/viewings/my/seller', { page, size: 20 }),
  confirmSeller: (id:string) => pu<ViewingResponse>(`/api/viewings/${id}/confirm-seller`),
  confirmBuyer:  (id:string) => pu<ViewingResponse>(`/api/viewings/${id}/confirm-buyer`),
  complete:      (id:string) => pu<ViewingResponse>(`/api/viewings/${id}/complete`),
  cancel:        (id:string, d:object) => pu<ViewingResponse>(`/api/viewings/${id}/cancel`, d),
}

// PAYMENT
export const paymentApi = {
  initiate: (d:object) => po<PaymentResponse>('/api/payments/initiate', d),
  getById:  (id:string) => g<PaymentResponse>(`/api/payments/${id}`),
  getConfig: () => g<{viewingFeeKes:number; profileAccessFeeKes:number; transactionCommissionPct:number}>('/api/payments/config'),
  my:       (page = 0) => g<PageResponse<PaymentResponse>>('/api/payments/my', { page, size: 20 }),
  pendingRelease: (page = 0) => g<PageResponse<PaymentResponse>>('/api/payments/admin/pending-release', { page, size: 20 }),
  audit:    (id:string) => g<PaymentAuditResponse[]>(`/api/payments/${id}/audit`),
  receipt:  (id:string) => g<PaymentReceiptResponse>(`/api/payments/${id}/receipt`),
  profileAccess: (sellerId:string) => g<{hasAccess:boolean}>(`/api/payments/profile-access/${sellerId}`),
}

// REVENUE
export const revenueApi = {
  summary:      () => g<RevenueSummaryResponse>('/api/revenue/summary'),
  all:          () => g<PageResponse<RevenueResponse>>('/api/revenue'),
  release:      (id:string, d:object) => pu(`/api/revenue/payments/${id}/release-escrow`, d),
  refund:       (id:string, reason:string) => pu<void>(`/api/revenue/payments/${id}/refund`, { reason }),
  audit:        (id:string) => g(`/api/revenue/payments/${id}/audit`),
  byReceipt:    (num:string) => g(`/api/revenue/receipts/${num}`),
  paymentReceipt:(id:string) => g(`/api/revenue/payments/${id}/receipt`),
}

// REVIEW
export const reviewApi = {
  create:       (d:object) => po<ReviewResponse>('/api/reviews', d),
  byProperty:   (pid:string) => g<PageResponse<ReviewResponse>>(`/api/reviews/property/${pid}`),
  bySeller:     (sid:string) => g<PageResponse<ReviewResponse>>(`/api/reviews/seller/${sid}`),
  sellerRating: (sid:string) => g<SellerRatingResponse>(`/api/reviews/seller/${sid}/rating`),
  myReviews:    (page = 0) => g<PageResponse<ReviewResponse>>('/api/reviews/my', { page, size: 20 }),
}

// DOCUMENTS
export interface UploadResponse { url:string; objectKey:string; category:string; sizeBytes:number }
export const documentApi = {
  upload: (file: File, category: string) => {
    const form = new FormData()
    form.append('file', file)
    form.append('category', category)
    return api.post<UploadResponse>('/api/documents/upload', form).then(r => r.data)
  },
}

export default api
