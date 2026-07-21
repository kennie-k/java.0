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
