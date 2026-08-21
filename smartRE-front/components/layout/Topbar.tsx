'use client'
import { useState } from 'react'
import Link from 'next/link'
import { Menu, Sun, Moon, LogOut, User, Settings, ChevronDown } from 'lucide-react'
import { useAuthStore } from '@/lib/store'
import { useUIStore, SIDEBAR_WIDTH, SIDEBAR_WIDTH_COLLAPSED } from '@/lib/uiStore'
import { useLogout } from '@/hooks/useLogout'
import { cn, fmt } from '@/lib/utils'

export default function Topbar({ onMenu, title }:{ onMenu():void; title?:string }) {
  const { user } = useAuthStore()
  const collapsed = useUIStore(s => s.collapsed)
  const [dark, setDark] = useState(() => typeof document !== 'undefined' && document.documentElement.classList.contains('dark'))
  const [menu, setMenu] = useState(false)
  const handleLogout = useLogout()

  const toggleTheme = () => {
    document.documentElement.classList.toggle('dark')
    localStorage.setItem('sre-theme', document.documentElement.classList.contains('dark') ? 'dark' : 'light')
    setDark(d => !d)
  }

  return (
    <header className="fixed top-0 right-0 left-0 z-30 h-16 bg-white/90 dark:bg-[#0B0B18]/90 backdrop-blur-md border-b border-base flex items-center gap-3 px-4">
      <button onClick={onMenu} className="lg:hidden btn-ghost !h-9 !w-9 !px-0" aria-label="Open menu"><Menu size={18}/></button>
      <div className="hidden lg:block shrink-0 transition-[width] duration-300 ease-in-out" style={{width: collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH}} />
      {title && <h1 className="font-display font-semibold text-lg text-gray-900 dark:text-white hidden sm:block">{title}</h1>}
      <div className="flex-1"/>

      <button onClick={toggleTheme} className="btn-ghost !h-9 !w-9 !px-0" title="Toggle theme" aria-label="Toggle theme">
        {dark ? <Sun size={17}/> : <Moon size={17}/>}
      </button>

      {user && (
        <div className="relative">
          <button onClick={() => setMenu(m => !m)}
            className="flex items-center gap-2 h-9 px-2.5 rounded-lg hover:bg-gray-100 dark:hover:bg-[#1A1A35] transition-colors">
            <div className="w-7 h-7 rounded-full bg-gold-500 text-white text-[11px] font-bold flex items-center justify-center shrink-0">
              {fmt.initials(user.fullName)}
            </div>
            <span className="text-[13px] font-medium text-gray-700 dark:text-gray-200 hidden sm:block">{user.fullName.split(' ')[0]}</span>
            <ChevronDown size={13} className="text-gray-400"/>
          </button>
          {menu && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenu(false)}/>
              <div className="absolute right-0 top-[calc(100%+8px)] w-56 card z-50 py-1 animate-fade-in">
                <div className="px-4 py-2.5 border-b border-base">
                  <p className="text-[13px] font-semibold text-gray-900 dark:text-white">{user.fullName}</p>
                  <p className="text-[11px] text-muted truncate">{user.email}</p>
                  <span className="mt-1.5 badge bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400">{user.role}</span>
                </div>
                {[
                  { icon:User, label:'Profile', href:'/profile' },
                  { icon:Settings, label:'Settings', href:'/profile' },
                ].map(i => (
                  <Link key={i.label} href={i.href} onClick={() => setMenu(false)}
                    className="flex items-center gap-2.5 px-4 py-2 text-[13px] text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-[#1A1A35] hover:pl-[18px] transition-all duration-150">
                    <i.icon size={14}/>{i.label}
                  </Link>
                ))}
                <div className="border-t border-base mt-1">
                  <button onClick={handleLogout}
                    className="w-full flex items-center gap-2.5 px-4 py-2 text-[13px] text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 hover:pl-[18px] transition-all duration-150">
                    <LogOut size={14}/>Log out
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </header>
  )
}
