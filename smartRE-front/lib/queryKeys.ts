export const queryKeys = {
  // Large unpaginated batch used only by the admin overview quick-search box.
  users: ['users', 'admin', 'all'] as const,
  // Real pagination for the Users management page — keyed by page so each
  // page's data is cached independently and doesn't collide with `users` above.
  usersPage: (page: number, size: number) => ['users', 'admin', 'page', page, size] as const,
  revenueSummary: ['admin', 'revenue', 'summary'] as const,
  revenueAll: ['admin', 'revenue', 'all'] as const,
  propertiesForOverview: ['admin', 'properties', 'overview'] as const,
  identityAdminQueue: (status: string) => ['admin', 'verification', 'identity', status] as const,
  identityFraudSummary: ['admin', 'verification', 'identity', 'fraud-summary'] as const,
  userAdminStats: ['admin', 'users', 'stats'] as const,
  propertyAdminStats: ['admin', 'properties', 'stats'] as const,
  ownershipAdminQueue: (status: string) => ['admin', 'verification', 'ownership', status] as const,
  reports: (status: string) => ['admin', 'reports', status] as const,
  reviewsAdmin: (visible: string, page: number) => ['admin', 'reviews', visible, page] as const,
  reviewsAdminStats: ['admin', 'reviews', 'stats'] as const,
  reportEvidence: (targetId: string) => ['admin', 'reports', 'evidence', targetId] as const,
  agentApplications: (status: string) => ['admin', 'agent-applications', status] as const,
  // Prefix key (also used for invalidating every cached page of a status
  // after a mutation) plus the paginated key actually used for fetching.
  listingsAdmin: (status: string) => ['admin', 'listings', status] as const,
  listingsAdminPage: (status: string, page: number, size: number) => ['admin', 'listings', status, 'page', page, size] as const,
  myListings: ['listings', 'mine'] as const,
  sellerProfile: (id: string) => ['sellers', id] as const,
  sellerProfileAccess: (id: string) => ['sellers', id, 'access'] as const,
  propertySearch: (filters: Record<string, unknown>) => ['properties', 'search', filters] as const,
  myViewings: (tab: string) => ['viewings', 'mine', tab] as const,
  dashboard: (role: string) => ['dashboard', role] as const,
}
