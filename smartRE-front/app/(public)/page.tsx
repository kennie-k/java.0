import type { Metadata } from 'next'
import HomeClient from './HomeClient'

export const metadata: Metadata = {
  title: 'SmartRE Kenya · Identity-Verified Property Marketplace',
  description:
    'Buy, sell and rent verified property in Kenya. Every seller passes National ID and KRA PIN checks, every title is confirmed against the Ardhisasa land registry, and every payment moves through M-Pesa escrow.',
  keywords: ['property Kenya', 'real estate Kenya', 'houses for sale Nairobi', 'land for sale Kenya', 'verified property listings', 'Ardhisasa', 'M-Pesa escrow property'],
  alternates: { canonical: '/' },
  openGraph: {
    title: 'SmartRE Kenya · Identity-Verified Property Marketplace',
    description: 'Buy and sell verified property in Kenya. National ID, KRA PIN and Ardhisasa title checks on every listing.',
    type: 'website',
    locale: 'en_KE',
    siteName: 'SmartRE Kenya',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'SmartRE Kenya · Identity-Verified Property Marketplace',
    description: 'Buy and sell verified property in Kenya with M-Pesa escrow protection.',
  },
}

const jsonLd = {
  '@context': 'https://schema.org',
  '@type': 'RealEstateAgent',
  name: 'SmartRE Kenya',
  description: 'Identity and title-verified real estate marketplace for Kenya, with M-Pesa escrow payments.',
  areaServed: { '@type': 'Country', name: 'Kenya' },
  url: 'https://smartre.co.ke',
  potentialAction: {
    '@type': 'SearchAction',
    target: 'https://smartre.co.ke/properties?keyword={search_term_string}',
    'query-input': 'required name=search_term_string',
  },
}

export default function HomePage() {
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <HomeClient/>
    </>
  )
}
