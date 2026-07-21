// ── AUTH ──────────────────────────────────────────────────────────────────────
export type Role = 'BUYER' | 'SELLER' | 'ADMIN'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  email: string
  role: Role
  verified: boolean
}

// ── USER ──────────────────────────────────────────────────────────────────────
export interface UserResponse {
  id: string
  fullName: string
  email: string
  phone?: string
  role: Role
  verified: boolean
  profileImage?: string
  accountType?: 'INDIVIDUAL' | 'COMPANY'
  companyName?: string
  companyRegNumber?: string
  kraPin?: string
  paybillNumber?: string
  tillNumber?: string
  bankAccountName?: string
  bankAccountNumber?: string
  bankName?: string
  bankBranch?: string
  bankSwiftCode?: string
  preferredPayoutMethod?: 'MPESA' | 'PAYBILL' | 'TILL' | 'BANK'
  payoutPhone?: string
  createdAt: string
}

// ── PROPERTY ──────────────────────────────────────────────────────────────────
export type PropertyType = 'HOUSE' | 'APARTMENT' | 'LAND' | 'COMMERCIAL' | 'VILLA'
export type ListingType = 'SALE' | 'RENT'
export type ListingStatus = 'DRAFT' | 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'SOLD' | 'RENTED' | 'WITHDRAWN'

export interface PropertyResponse {
  id: string
  sellerId: string
  title: string
  description?: string
  propertyType: PropertyType
  listingType: ListingType
  status: ListingStatus
  county: string
  subCounty?: string
  city?: string
  locationDescription?: string
  latitude?: number
  longitude?: number
  price: number
  bedrooms?: number
  bathrooms?: number
  areaSqm?: number
  yearBuilt?: number
  imageUrls: string[]
  sellerIdentityVerified: boolean
  propertyOwnershipVerified: boolean
  fullyTrusted: boolean
  parcelNumber?: string
  titleDeedNumber?: string
  viewCount: number
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// ── VERIFICATION ──────────────────────────────────────────────────────────────
export type VerifStatus = 'DRAFT'|'SUBMITTED'|'AI_SCREENING'|'HUMAN_REVIEW'|'APPROVED'|'REJECTED'|'REQUIRES_RESUBMISSION'|'EXPIRED'

export interface IdentityDocumentResponse {
  id: string
  documentUrl: string
  documentCategory?: string
  aiAuthenticityScore?: number
  aiTamperDetected?: boolean
  aiSignatureDetected?: boolean
  aiSealDetected?: boolean
  aiScreeningNotes?: string
  humanVerified?: boolean
  humanReviewNotes?: string
  uploadedAt?: string
}

export interface IdentityVerificationResponse {
  id: string
  userId: string
  status: VerifStatus
  identityScore: number
  rejectionReason?: string
  resubmissionNotes?: string
  expiresAt?: string
  expired: boolean
  createdAt: string
  updatedAt: string
  documents: IdentityDocumentResponse[]
  missingRequiredDocuments?: string[]
  fraudStrikeCount: number
}

export interface TrustStatusResponse {
  userId: string
  identityVerified: boolean
  identityScore?: number
  identityExpired: boolean
  identityExpiresAt?: string
  propertyId?: string
  ownershipVerified: boolean
  ownershipScore?: number
  ministryLandsConfirmed?: boolean
  encumbranceClear?: boolean
  fullyTrusted: boolean
  message?: string
}

// ── VIEWING ───────────────────────────────────────────────────────────────────
export type ViewingStatus = 'PENDING_FEE'|'REQUESTED'|'CONFIRMED'|'COMPLETED'|'CANCELLED'|'NO_SHOW'

export interface ViewingResponse {
  id: string
  propertyId: string
  buyerId: string
  sellerId: string
  scheduledAt: string
  completedAt?: string
  status: ViewingStatus
  notes?: string
  buyerConfirmed: boolean
  sellerConfirmed: boolean
  viewingFeePaymentId?: string
  viewingFeeStatus?: string
  cancellationReason?: string
  createdAt?: string
  updatedAt?: string
}

// ── PAYMENT ───────────────────────────────────────────────────────────────────
export type PaymentStatus = 'PENDING'|'STK_PUSHED'|'COMPLETED'|'FAILED'|'CANCELLED'|'REFUNDED'
export type PaymentType = 'FULL_PAYMENT'|'DEPOSIT'|'VIEWING_FEE'|'COMMISSION'|'PROFILE_ACCESS'

export interface PaymentResponse {
  id: string
  buyerId: string
  sellerId: string
  propertyId: string
  paymentType: PaymentType
  status: PaymentStatus
  currency: string
  phoneNumber: string
  amount: number
  mpesaCheckoutRequestId?: string
  mpesaReceiptNumber?: string
  escrowReleased: boolean
  failureReason?: string
  createdAt?: string
  updatedAt?: string
}

export interface PaymentAuditResponse {
  id: string
  paymentId: string
  revenueId?: string
  eventType: string
  previousStatus?: string
  newStatus: string
  actorId?: string
  actorRole: string
  actorIp?: string
  mpesaReceipt?: string
  amountKes: number
  detail?: string
  createdAt: string
}

export interface PaymentReceiptResponse {
  id: string
  receiptNumber: string
  paymentId: string
  buyerId: string
  sellerId: string
  propertyId: string
  paymentType: string
  grossAmount: number
  platformFee: number
  sellerPayout: number
  currency: string
  mpesaReceipt: string
  payerPhone: string
  issuedAt: string
}

// ── REVENUE ───────────────────────────────────────────────────────────────────
export interface RevenueSummaryResponse {
  totalPlatformFees: number
  viewingFeeRevenue: number
  commissionRevenue: number
  thisMonthRevenue: number
  lastMonthRevenue: number
  totalTransactions?: number
  thisMonthTransactions?: number
  currency: string
}

export interface RevenueResponse {
  id: string
  paymentId: string
  buyerId: string
  sellerId: string
  propertyId: string
  revenueType: string
  grossAmount: number
  platformFee: number
  sellerPayout: number
  feePercentage: number
  currency: string
  status: string
  payoutMethod?: string
  payoutFailureReason?: string
  releasedByAdminId?: string
  releaseNotes?: string
  createdAt: string
}

// ── REVIEW ────────────────────────────────────────────────────────────────────
export interface ReviewResponse {
  id: string
  reviewerId: string
  sellerId: string
  propertyId: string
  paymentId: string
  rating: number
  comment?: string
  verified: boolean
  createdAt: string
}

export interface SellerRatingResponse {
  sellerId: string
  averageRating: number
  reviewCount: number
}
