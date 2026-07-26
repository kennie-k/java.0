
export type Role = 'BUYER' | 'SELLER' | 'AGENT' | 'ADMIN'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  email: string
  role: Role
  verified: boolean
}

export interface UserResponse {
  id: string
  fullName: string
  email: string
  phone?: string
  role: Role
  verified: boolean
  active: boolean
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
  duplicateParcelFlag?: boolean
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

export type BadgeLevel = 'NONE' | 'BASIC' | 'VERIFIED' | 'GOLD'

export interface IdentityVerificationResponse {
  id: string
  userId: string
  status: VerifStatus
  identityScore: number
  badgeLevel?: BadgeLevel
  rejectionReason?: string
  resubmissionNotes?: string
  expiresAt?: string
  expired: boolean
  createdAt: string
  updatedAt: string
  documents: IdentityDocumentResponse[]
  missingRequiredDocuments?: string[]
  fraudStrikeCount: number
  permanentlyBanned: boolean
}

export type OwnershipVerifStatus =
  | 'DRAFT' | 'SUBMITTED' | 'AI_SCREENING' | 'MINISTRY_LANDS_CHECK' | 'ENCUMBRANCE_CHECK'
  | 'LEGAL_REVIEW' | 'HUMAN_REVIEW' | 'APPROVED' | 'REJECTED' | 'REQUIRES_RESUBMISSION'

export type OwnershipPropertyType = 'FREEHOLD' | 'LEASEHOLD' | 'SECTIONAL_TITLE' | 'AGRICULTURAL' | 'COMMERCIAL'

export interface DocumentRequirementResponse {
  documentCategory: string
  isMandatory: boolean
  description?: string
  kenyaLawRef?: string
  uploaded: boolean
}

export interface OwnershipDocumentResponse {
  id: string
  documentCategory: string
  documentUrl: string
  lcAdvocateStampPresent?: boolean
  lcAdvocateSignaturePresent?: boolean
  lcCommissionerOathsPresent?: boolean
  lcOfficialSealPresent?: boolean
  lcOwnerSignaturePresent?: boolean
  lcWitnessSignaturesPresent?: boolean
  lcDatePresent?: boolean
  lcParcelNumberMatches?: boolean
  lcOriginalDocumentConfirmed?: boolean
  aiAuthenticityScore?: number
  aiTamperDetected?: boolean
  aiAlterationDetected?: boolean
  aiFontConsistency?: boolean
  aiDateSequenceValid?: boolean
  aiScreeningNotes?: string
  humanLegalApproved?: boolean
  humanReviewNotes?: string
  uploadedAt?: string
}

export interface OwnershipVerificationResponse {
  id: string
  propertyId: string
  sellerIdentityVerificationId: string
  status: OwnershipVerifStatus
  propertyType: OwnershipPropertyType
  county?: string
  parcelNumber?: string
  titleDeedNumber?: string
  lrNumber?: string
  ownershipScore?: number
  ministryLandsConfirmed?: boolean
  encumbranceClear?: boolean
  rejectionReason?: string
  createdAt: string
  updatedAt: string
  documents: OwnershipDocumentResponse[]
  missingDocuments?: DocumentRequirementResponse[]
  allRequiredDocuments?: DocumentRequirementResponse[]
}

export interface TrustStatusResponse {
  userId: string
  identityVerified: boolean
  identityStatus?: VerifStatus
  badgeLevel?: BadgeLevel
  identityScore?: number
  identityExpired: boolean
  identityExpiresAt?: string
  propertyId?: string
  ownershipVerified: boolean
  ownershipStatus?: OwnershipVerifStatus
  ownershipScore?: number
  ministryLandsConfirmed?: boolean
  encumbranceClear?: boolean
  fullyTrusted: boolean
  message?: string
}

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

export type ReportTargetType = 'LISTING' | 'USER' | 'REVIEW'
export type ReportReason = 'FAKE_LISTING' | 'SCAM_AGENT' | 'DUPLICATE_LISTING' | 'OFF_PLATFORM_PAYMENT_REQUEST' | 'FAKE_REVIEW' | 'OTHER'
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'

export interface ReportResponse {
  id: string
  reporterId: string
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  details?: string
  status: ReportStatus
  adminNotes?: string
  resolvedBy?: string
  resolvedAt?: string
  createdAt: string
  updatedAt: string
}

export type AgentApplicationStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface AgentApplicationResponse {
  id: string
  userId: string
  status: AgentApplicationStatus
  businessName?: string
  businessDocUrl: string
  rejectionReason?: string
  reviewedBy?: string
  reviewedAt?: string
  createdAt: string
  updatedAt: string
}
