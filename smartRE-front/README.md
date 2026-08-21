# SmartRE Kenya — Next.js Frontend

## Stack
- **Framework**: Next.js 14 (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS + custom CSS variables
- **State**: Zustand (auth + theme)
- **Forms**: React Hook Form + Zod
- **Data fetching**: Axios
- **Charts**: Recharts
- **UI**: Custom component library (no external UI lib)
- **Icons**: Lucide React
- **Toasts**: React Hot Toast

## Getting started

```bash
npm install
npm run dev
```

Opens at http://localhost:3000

Backend must be running at http://localhost:8080

## Environment

Create `.env.local`:
```
NEXT_PUBLIC_API_URL=http://localhost:8080

# Optional — production S3 bucket that property/document photos are served
# from. Used to build next.config.js's images.remotePatterns allowlist (falls
# back to the default bucket below if unset). Only needed outside local dev.
NEXT_PUBLIC_S3_PUBLIC_URL=https://smartre-documents.s3.amazonaws.com
```

Server-only (not `NEXT_PUBLIC_*` — never shipped to the browser):
```
# Optional. The same HMAC key the backend services sign sre_token JWTs with
# (smartRE/.env JWT_SECRET). When set, middleware.ts verifies the token's
# signature with `jose` before using its claims for redirect decisions. When
# unset, middleware.ts falls back to an unverified decode used only for UX
# redirects — see the comment at the top of middleware.ts for what that does
# and does not protect.
JWT_SECRET=
```

## Folder structure

```
app/
  (auth)/          # Login + Register (no sidebar)
  (dashboard)/     # All user pages (with sidebar)
    dashboard/     # Home dashboard
    properties/    # Browse + detail + new listing
    verification/  # Seller identity verification flow
    viewings/      # Viewing management
    payments/      # Payment history + detail + audit trail
    reviews/       # Reviews
    profile/       # Account settings
  (admin)/         # Admin-only pages (role guard)
    revenue/       # Revenue summary + escrow release
    users/         # User management
    verification-queue/ # Identity verification review queue

components/
  ui/              # Design system: Button, Input, Select, Textarea, Card, Badge, Modal
  layout/          # Sidebar, Topbar

lib/
  api.ts           # All API calls — every backend endpoint mapped
  store.ts         # Zustand auth store
  utils.ts         # Formatting utilities

types/
  index.ts         # TypeScript interfaces matching all backend DTOs
```

## Roles
- **BUYER**: Browse properties, schedule viewings, make payments, write reviews
- **SELLER**: List properties, manage viewings, track payments, identity verification
- **ADMIN**: All above + revenue dashboard, escrow release, user management, verification queue

## Theme
- Orange `#FF6C37` (exact Postman orange) as primary accent
- Space Grotesk for display/headings
- Inter for body text
- Full dark mode via Tailwind `dark:` classes + `class` strategy
- Toggle persisted in `localStorage`
