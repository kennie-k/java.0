'use client'
import { useState } from 'react'
import { usePathname } from 'next/navigation'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import Sidebar from '@/components/layout/Sidebar'
import Topbar from '@/components/layout/Topbar'
import { PageLoader } from '@/components/ui/Modal'

const titles: Record<string,string> = {
  '/dashboard':'Dashboard','/listings':'My Listings','/properties/new':'New Listing',
  '/verification':'Verification','/ownership':'Land Title','/viewings':'Viewings','/payments':'Payments',
  '/reviews':'Reviews','/profile':'Profile',
}

export default function DashboardLayout({ children }:{ children:React.ReactNode }) {
  const [sidebar, setSidebar] = useState(false)
  const { ready } = useAuthGuard()
  const path = usePathname()

  if (!ready) return <PageLoader/>

  const title = Object.entries(titles).find(([k]) => path===k || (k!=='/' && path.startsWith(k)))?.[1] || ''

  return (
    <div className="min-h-screen bg-surface">
      <Sidebar open={sidebar} onClose={() => setSidebar(false)}/>
      <Topbar onMenu={() => setSidebar(true)} title={title}/>
      <main className="pt-16 min-h-screen lg:pl-[260px]">
        <div className="p-4 sm:p-6 max-w-7xl mx-auto animate-fade-in">{children}</div>
      </main>
    </div>
  )
}
