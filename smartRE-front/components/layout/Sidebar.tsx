'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useAuthStore } from '@/lib/store'
import { cn, fmt } from '@/lib/utils'
import {
  LayoutDashboard, Building2, ShieldCheck, Calendar,
  CreditCard, Star, BarChart3, Users, X, ListChecks, Search, Gauge, Landmark,
} from 'lucide-react'

const nav = [
  { label:'Dashboard',    href:'/dashboard',           icon:LayoutDashboard, roles:['BUYER','SELLER','ADMIN'] },
  { label:'Browse',       href:'/properties',          icon:Search,          roles:['BUYER','SELLER','ADMIN'] },
  { label:'My Listings',  href:'/listings',            icon:Building2,       roles:['SELLER'] },
  { label:'Verification', href:'/verification',        icon:ShieldCheck,     roles:['SELLER'] },
  { label:'Land Title',   href:'/ownership',           icon:Landmark,        roles:['SELLER'] },
  { label:'Viewings',     href:'/viewings',            icon:Calendar,        roles:['BUYER','SELLER'] },
  { label:'Payments',     href:'/payments',            icon:CreditCard,      roles:['BUYER','SELLER'] },
  { label:'Reviews',      href:'/reviews',             icon:Star,            roles:['BUYER','SELLER'] },
]
const adminNav = [
  { label:'Command Center', href:'/overview',          icon:Gauge },
  { label:'Revenue',      href:'/revenue',             icon:BarChart3 },
  { label:'Users',        href:'/users',               icon:Users },
  { label:'Verif Queue',  href:'/verification-queue',  icon:ListChecks },
]

export default function Sidebar({ open, onClose }:{ open:boolean; onClose():void }) {
  const path = usePathname()
  const user = useAuthStore(s => s.user)
  const isActive = (href: string) => href === '/' ? path === '/' : path.startsWith(href)
  const visible = nav.filter(n => !user || n.roles.includes(user.role))

  return (
    <>
      {open && <div onClick={onClose} className="fixed inset-0 z-40 bg-black/50 lg:hidden" />}
      <aside className={cn(
        'fixed inset-y-0 left-0 z-50 flex flex-col border-r border-base bg-surface-2 transition-transform duration-300',
        'lg:translate-x-0',
        open ? 'translate-x-0' : '-translate-x-full',
      )} style={{width:'var(--sidebar-w)' as string}}>

        {/* Logo */}
        <div className="flex items-center justify-between h-16 px-4 border-b border-base shrink-0">
          <Link href="/" className="flex items-center gap-2.5" onClick={onClose}>
            <div className="w-8 h-8 bg-gold-500 rounded-lg flex items-center justify-center text-white font-display font-bold text-base">S</div>
            <span className="font-display font-bold text-lg text-gray-900 dark:text-white">SmartRE</span>
          </Link>
          <button onClick={onClose} className="lg:hidden w-8 h-8 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 flex items-center justify-center text-gray-500" aria-label="Close menu">
            <X size={16}/>
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto py-3 px-3 space-y-0.5">
          {visible.map(item => (
            <Link key={item.href} href={item.href} onClick={onClose}
              className={cn(isActive(item.href) ? 'sidebar-link-active' : 'sidebar-link')}>
              {isActive(item.href) && <span className="absolute left-0 top-1/4 bottom-1/4 w-0.5 bg-gold-500 rounded-r" />}
              <item.icon size={17} />
              {item.label}
            </Link>
          ))}

          {user?.role === 'ADMIN' && (
            <>
              <div className="pt-4 pb-1 px-3">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600">Admin</p>
              </div>
              {adminNav.map(item => (
                <Link key={item.href} href={item.href} onClick={onClose}
                  className={cn(isActive(item.href) ? 'sidebar-link-active' : 'sidebar-link')}>
                  <item.icon size={17}/>{item.label}
                </Link>
              ))}
            </>
          )}
        </nav>

        {/* User */}
        {user && (
          <div className="p-3 border-t border-base shrink-0">
            <Link href="/profile" onClick={onClose}
              className="flex items-center gap-3 p-2.5 rounded-xl hover:bg-gray-100 dark:hover:bg-[#1A1A35] transition-colors">
              <div className="w-9 h-9 rounded-full bg-gold-500 text-white flex items-center justify-center text-xs font-bold shrink-0">
                {fmt.initials(user.fullName)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">{user.fullName}</p>
                <p className="text-xs text-muted truncate">{user.role}</p>
              </div>
            </Link>
          </div>
        )}
      </aside>
    </>
  )
}
