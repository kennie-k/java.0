'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useAuthStore } from '@/lib/store'
import { useUIStore, SIDEBAR_WIDTH, SIDEBAR_WIDTH_COLLAPSED } from '@/lib/uiStore'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { cn, fmt } from '@/lib/utils'
import { SELLER_ROLES } from '@/lib/roles'
import {
  LayoutDashboard, Building2, ShieldCheck, Calendar,
  CreditCard, Star, BarChart3, Users, X, ListChecks, Search, Gauge, Landmark, Briefcase, Flag,
  ChevronsLeft, ChevronsRight,
} from 'lucide-react'

const nav = [
  { label:'Dashboard',    href:'/dashboard',           icon:LayoutDashboard, roles:['BUYER',...SELLER_ROLES,'ADMIN'] },
  { label:'Browse',       href:'/properties',          icon:Search,          roles:['BUYER',...SELLER_ROLES,'ADMIN'] },
  { label:'My Listings',  href:'/listings',            icon:Building2,       roles:SELLER_ROLES },
  { label:'Verification', href:'/verification',        icon:ShieldCheck,     roles:SELLER_ROLES },
  { label:'Land Title',   href:'/ownership',           icon:Landmark,        roles:SELLER_ROLES },
  { label:'Become an Agent', href:'/agent-application', icon:Briefcase,      roles:['SELLER'] },
  { label:'Viewings',     href:'/viewings',            icon:Calendar,        roles:['BUYER',...SELLER_ROLES] },
  { label:'Payments',     href:'/payments',            icon:CreditCard,      roles:['BUYER',...SELLER_ROLES] },
  { label:'Reviews',      href:'/reviews',             icon:Star,            roles:['BUYER',...SELLER_ROLES] },
]
const adminNav = [
  { label:'Command Center', href:'/overview',          icon:Gauge },
  { label:'Revenue',      href:'/revenue',             icon:BarChart3 },
  { label:'Users',        href:'/users',               icon:Users },
  { label:'Verif Queue',  href:'/verification-queue',  icon:ListChecks },
  { label:'Listings',     href:'/manage-listings',     icon:Building2 },
  { label:'Reports',      href:'/reports',             icon:Flag },
  { label:'Agent Apps',   href:'/agent-applications',  icon:Briefcase },
]

export default function Sidebar({ open, onClose }:{ open:boolean; onClose():void }) {
  const path = usePathname()
  const user = useAuthStore(s => s.user)
  const collapsed = useUIStore(s => s.collapsed)
  const toggleCollapsed = useUIStore(s => s.toggleCollapsed)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const rail = isDesktop && collapsed

  const isActive = (href: string) => href === '/' ? path === '/' : path.startsWith(href)
  const visible = nav.filter(n => !user || n.roles.includes(user.role))

  const NavItem = ({ item }: { item: { label:string; href:string; icon:any } }) => (
    <Link key={item.href} href={item.href} onClick={onClose}
      className={cn('relative group', isActive(item.href) ? 'sidebar-link-active' : 'sidebar-link', rail && 'justify-center px-0 gap-0')}>
      {isActive(item.href) && <span className="absolute left-0 top-1/4 bottom-1/4 w-0.5 bg-gold-500 rounded-r" />}
      <item.icon size={17} className="shrink-0"/>
      {!rail && item.label}
      {rail && (
        <span className="pointer-events-none absolute left-full ml-3 whitespace-nowrap rounded-md bg-gray-900 dark:bg-black px-2.5 py-1.5 text-[12px] font-medium text-white opacity-0 scale-95 origin-left group-hover:opacity-100 group-hover:scale-100 transition-all duration-150 z-50 shadow-lg">
          {item.label}
        </span>
      )}
    </Link>
  )

  return (
    <>
      {open && <div onClick={onClose} className="fixed inset-0 z-40 bg-black/50 lg:hidden" />}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col border-r border-base bg-surface-2',
          'transition-[width,transform] duration-300 ease-in-out',
          'lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
        style={{ width: isDesktop ? (collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH) : SIDEBAR_WIDTH }}
      >
        <div className={cn('flex items-center h-16 px-4 border-b border-base shrink-0', rail ? 'justify-center px-2' : 'justify-between')}>
          <Link href="/" className="flex items-center gap-2.5 min-w-0" onClick={onClose}>
            <div className="w-8 h-8 bg-gold-500 rounded-lg flex items-center justify-center text-white font-display font-bold text-base shrink-0">S</div>
            {!rail && <span className="font-display font-bold text-lg text-gray-900 dark:text-white truncate">SmartRE</span>}
          </Link>
          {!rail && (
            <button onClick={onClose} className="lg:hidden w-8 h-8 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 flex items-center justify-center text-gray-500 transition-colors" aria-label="Close menu">
              <X size={16}/>
            </button>
          )}
        </div>

        <nav className="flex-1 overflow-y-auto overflow-x-hidden py-3 px-3 space-y-0.5">
          {visible.map(item => <NavItem key={item.href} item={item}/>)}

          {user?.role === 'ADMIN' && (
            <>
              <div className={cn('pt-4 pb-1', rail ? 'flex justify-center' : 'px-3')}>
                {rail ? <div className="w-6 border-t border-base"/> : (
                  <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600">Admin</p>
                )}
              </div>
              {adminNav.map(item => <NavItem key={item.href} item={item}/>)}
            </>
          )}
        </nav>

        <button onClick={toggleCollapsed}
          className={cn('hidden lg:flex items-center h-10 border-t border-base shrink-0 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-[#1A1A35] transition-colors',
            rail ? 'justify-center' : 'justify-start gap-2 px-4 text-[12px] font-medium')}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          {collapsed ? <ChevronsRight size={16}/> : <><ChevronsLeft size={16}/>Collapse</>}
        </button>

        {user && (
          <div className="p-3 border-t border-base shrink-0">
            <Link href="/profile" onClick={onClose}
              className={cn('group relative flex items-center gap-3 p-2.5 rounded-xl hover:bg-gray-100 dark:hover:bg-[#1A1A35] transition-colors', rail && 'justify-center p-2')}>
              <div className="w-9 h-9 rounded-full bg-gold-500 text-white flex items-center justify-center text-xs font-bold shrink-0">
                {fmt.initials(user.fullName)}
              </div>
              {!rail && (
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-900 dark:text-white truncate">{user.fullName}</p>
                  <p className="text-xs text-muted truncate">{user.role}</p>
                </div>
              )}
              {rail && (
                <span className="pointer-events-none absolute left-full ml-3 bottom-1 whitespace-nowrap rounded-md bg-gray-900 dark:bg-black px-2.5 py-1.5 text-[12px] font-medium text-white opacity-0 scale-95 origin-left group-hover:opacity-100 group-hover:scale-100 transition-all duration-150 z-50 shadow-lg">
                  {user.fullName} · {user.role}
                </span>
              )}
            </Link>
          </div>
        )}
      </aside>
    </>
  )
}
