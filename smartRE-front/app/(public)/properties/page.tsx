import type { Metadata } from 'next'
import { Suspense } from 'react'
import PropertiesClient from './PropertiesClient'
import { PageLoader } from '@/components/ui/Modal'

export const metadata: Metadata = {
  title: 'Property Listings in Kenya',
  description: 'Browse identity- and title-verified houses, apartments, land and commercial property for sale or rent across Kenya. Filter by county, price, type and bedrooms.',
  alternates: { canonical: '/properties' },
}

export default function PropertiesPage() {
  return (
    <Suspense fallback={<PageLoader/>}>
      <PropertiesClient/>
    </Suspense>
  )
}
