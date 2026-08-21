import type { Metadata, Viewport } from 'next'
import { Toaster } from 'react-hot-toast'
import Providers from './providers'
import ErrorTracking from '@/components/ErrorTracking'
import './globals.css'

export const metadata: Metadata = {
  metadataBase: new URL('https://smartre.co.ke'),
  title: { default:'SmartRE Kenya · Identity-Verified Property Marketplace', template:'%s · SmartRE' },
  description:'Verified real estate marketplace for Kenya. Every seller passes National ID, KRA PIN and Ardhisasa land title checks before listing. Pay safely via M-Pesa escrow.',
  keywords: ['property Kenya','real estate Kenya','houses for sale Nairobi','land for sale Kenya','verified property listings','Ardhisasa','M-Pesa escrow'],
  robots: { index: true, follow: true },
  openGraph: { siteName: 'SmartRE Kenya', locale: 'en_KE', type: 'website' },
  manifest: '/manifest.json',
}

export const viewport: Viewport = { themeColor: '#C9A227' }

export default function RootLayout({ children }:{ children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{__html:`
          (function(){
            const t = localStorage.getItem('sre-theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
            if(t==='dark') document.documentElement.classList.add('dark');
          })()
        `}} />
      </head>
      <body>
        <ErrorTracking />
        <Providers>{children}</Providers>
        <Toaster position="top-right" toastOptions={{
          style:{ background:'var(--toast-bg,#fff)', color:'#111', border:'1px solid #e5e7eb', borderRadius:10 },
          success:{ iconTheme:{ primary:'#C9A227', secondary:'#fff' } },
          duration: 3500,
        }}/>
      </body>
    </html>
  )
}
