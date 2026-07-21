'use client'
import { useState } from 'react'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import Sidebar from '@/components/layout/Sidebar'
import Topbar from '@/components/layout/Topbar'
import { PageLoader } from '@/components/ui/Modal'

export default function AdminLayout({ children }:{ children:React.ReactNode }) {
  const [sidebar, setSidebar] = useState(false)
  const { ready } = useAuthGuard(['ADMIN'], '/')

  if (!ready) return <PageLoader/>

  return (
    <div className="min-h-screen bg-surface">
      <Sidebar open={sidebar} onClose={() => setSidebar(false)}/>
      <Topbar onMenu={() => setSidebar(true)}/>
      <main className="pt-16 min-h-screen lg:pl-[260px]">
        <div className="p-6 max-w-7xl mx-auto animate-fade-in">{children}</div>
      </main>
    </div>
  )
}
