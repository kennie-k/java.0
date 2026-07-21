import type { Metadata } from 'next'
import { Suspense } from 'react'
import SellerProfileClient from './SellerProfileClient'
import { PageLoader } from '@/components/ui/Modal'

export const metadata: Metadata = {
  title: 'Seller Profile',
  description: 'View a seller\'s identity and title verification status, active listings, and buyer reviews on SmartRE Kenya.',
}

export default function SellerProfilePage() {
  return (
    <Suspense fallback={<PageLoader/>}>
      <SellerProfileClient/>
    </Suspense>
  )
}
