'use client'
import { useState, useEffect } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { motion } from 'framer-motion'
import { Menu, X, Search, Sun, Moon, ShieldCheck, ChevronDown, LayoutDashboard, LogOut } from 'lucide-react'
import { useAuthStore } from '@/lib/store'
import { authApi } from '@/lib/api'
import { cn, fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const links = [
  { label: 'Buy',        href: '/properties?listingType=SALE' },
  { label: 'Rent',       href: '/properties?listingType=RENT' },
  { label: 'Land',       href: '/properties?propertyType=LAND' },
  { label: 'Commercial', href: '/properties?propertyType=COMMERCIAL' },
  { label: 'How it works', href: '/#how-it-works' },
]

export default function PublicHeader() {
  const { user, logout } = useAuthStore()
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const [dark, setDark] = useState(false)
  const [menu, setMenu] = useState(false)

  useEffect(() => {
    setDark(document.documentElement.classList.contains('dark'))
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const toggleTheme = () => {
    document.documentElement.classList.toggle('dark')
    localStorage.setItem('sre-theme', document.documentElement.classList.contains('dark') ? 'dark' : 'light')
    setDark(d => !d)
  }

  const handleLogout = async () => {
    try { await authApi.logout() } catch {}
    logout(); toast.success('Logged out'); router.push('/')
  }

  return (
    <motion.header
      initial={{ y: -56, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ type: 'spring', stiffness: 260, damping: 24 }}
      className={cn(
        'sticky top-0 z-50 transition-all duration-200',
        scrolled ? 'bg-white/90 dark:bg-[#0B0B18]/90 backdrop-blur-md border-b border-base shadow-sm' : 'bg-transparent border-b border-transparent'
      )}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-13 flex items-center gap-3" style={{ height: 52 }}>
        <Link href="/" className="flex items-center gap-1.5 shrink-0">
          <div className="w-6 h-6 bg-gold-500 rounded-md flex items-center justify-center text-white font-display font-bold text-[13px]">S</div>
          <span className="font-display font-bold text-[15px] text-gray-900 dark:text-white">SmartRE</span>
        </Link>

        <nav className="hidden lg:flex items-center gap-0.5 ml-3">
          {links.map(l => (
            <Link key={l.label} href={l.href}
              className="px-2.5 py-1.5 rounded-md text-[12px] font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/5 hover:text-gray-900 dark:hover:text-white transition-colors">
              {l.label}
            </Link>
          ))}
        </nav>

        <div className="flex-1"/>

        <Link href="/properties" className="hidden md:flex items-center gap-1.5 h-7 px-3 rounded-full border border-base text-[12px] text-muted hover:border-gold-300 hover:text-gray-700 dark:hover:text-gray-200 transition-colors">
          <Search size={12}/> Search properties
        </Link>

        <button onClick={toggleTheme} className="btn-ghost !h-7 !w-7 !px-0 shrink-0" title="Toggle theme" aria-label="Toggle theme">
          {dark ? <Sun size={14}/> : <Moon size={14}/>}
        </button>

        {user ? (
          <div className="relative shrink-0">
            <button onClick={() => setMenu(m => !m)} className="flex items-center gap-1.5 h-7 pl-1 pr-2 rounded-full hover:bg-gray-100 dark:hover:bg-white/5 transition-colors">
              <div className="w-6 h-6 rounded-full bg-gold-500 text-white text-[10px] font-bold flex items-center justify-center">{fmt.initials(user.fullName)}</div>
              <ChevronDown size={12} className="text-gray-400 hidden sm:block"/>
            </button>
            {menu && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setMenu(false)}/>
                <div className="absolute right-0 top-[calc(100%+8px)] w-48 card z-50 py-1 animate-fade-in">
                  <div className="px-3 py-2 border-b border-base">
                    <p className="text-[12px] font-semibold text-gray-900 dark:text-white truncate">{user.fullName}</p>
                    <p className="text-[10px] text-muted truncate">{user.email}</p>
                  </div>
                  <Link href={user.role === 'ADMIN' ? '/overview' : '/dashboard'} onClick={() => setMenu(false)}
                    className="flex items-center gap-2 px-3 py-2 text-[12px] text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5 transition-colors">
                    <LayoutDashboard size={13}/>{user.role === 'ADMIN' ? 'Admin console' : 'Dashboard'}
                  </Link>
                  <button onClick={handleLogout} className="w-full flex items-center gap-2 px-3 py-2 text-[12px] text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors border-t border-base mt-1">
                    <LogOut size={13}/>Log out
                  </button>
                </div>
              </>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-1.5 shrink-0">
            <Link href="/login" className="btn-ghost !h-7 !px-2.5">Log in</Link>
            <Link href="/register" className="btn-primary !h-7 !px-2.5"><ShieldCheck size={12}/>Get started</Link>
          </div>
        )}

        <button onClick={() => setOpen(o => !o)} className="lg:hidden btn-ghost !h-7 !w-7 !px-0 shrink-0" aria-label="Menu">
          {open ? <X size={16}/> : <Menu size={16}/>}
        </button>
      </div>

      {open && (
        <div className="lg:hidden border-t border-base bg-white dark:bg-[#0B0B18] px-4 py-2.5 space-y-0.5 animate-fade-in">
          {links.map(l => (
            <Link key={l.label} href={l.href} onClick={() => setOpen(false)}
              className="block px-2.5 py-2 rounded-md text-[13px] font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/5">
              {l.label}
            </Link>
          ))}
        </div>
      )}
    </motion.header>
  )
}
