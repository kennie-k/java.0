'use client'
import { useState } from 'react'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { useUIStore, SIDEBAR_WIDTH, SIDEBAR_WIDTH_COLLAPSED } from '@/lib/uiStore'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import Sidebar from '@/components/layout/Sidebar'
import Topbar from '@/components/layout/Topbar'
import { PageLoader } from '@/components/ui/Modal'

export default function AdminLayout({ children }:{ children:React.ReactNode }) {
  const [sidebar, setSidebar] = useState(false)
  const { ready } = useAuthGuard(['ADMIN'], '/')
  const collapsed = useUIStore(s => s.collapsed)
  const isDesktop = useMediaQuery('(min-width: 1024px)')

  if (!ready) return <PageLoader/>

  return (
    <div className="min-h-screen bg-surface">
      <Sidebar open={sidebar} onClose={() => setSidebar(false)}/>
      <Topbar onMenu={() => setSidebar(true)}/>
      <main className="pt-16 min-h-screen transition-[padding-left] duration-300 ease-in-out"
        style={{ paddingLeft: isDesktop ? (collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH) : 0 }}>
        <div className="p-6 max-w-7xl mx-auto animate-fade-in">{children}</div>
      </main>
    </div>
  )
}
