'use client'
import { useEffect, useState } from 'react'
import Link from 'next/link'
import { usePathname, useSearchParams } from 'next/navigation'
import { useAuthStore } from '@/lib/store'
import { useUIStore, SIDEBAR_WIDTH, SIDEBAR_WIDTH_COLLAPSED } from '@/lib/uiStore'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { cn, fmt } from '@/lib/utils'
import { SELLER_ROLES } from '@/lib/roles'
import {
  LayoutDashboard, Building2, ShieldCheck, Calendar,
  CreditCard, Star, BarChart3, Users, X, ListChecks, Search, Gauge, Landmark, Briefcase, Flag,
  ChevronsLeft, ChevronsRight, ChevronDown,
  List, Plus, UserCheck, Home, Store, PieChart, Receipt, CheckCircle, FileEdit, Ban, AlertCircle, XCircle, Clock, EyeOff,
} from 'lucide-react'

type Child = { label:string; href:string; roles?:string[]; icon?:any }
type NavItem = { label:string; href:string; icon:any; roles?:string[]; children?:Child[] }

const nav: NavItem[] = [
  { label:'Dashboard',    href:'/dashboard',           icon:LayoutDashboard, roles:['BUYER',...SELLER_ROLES,'ADMIN'] },
  { label:'Browse',       href:'/properties',          icon:Search,          roles:['BUYER',...SELLER_ROLES,'ADMIN'] },
  { label:'My Listings',  href:'/listings',            icon:Building2,       roles:SELLER_ROLES,
    children:[
      { label:'All listings',     href:'/listings',        icon:List },
      { label:'Add new listing',  href:'/properties/new',  icon:Plus },
    ] },
  { label:'Verification', href:'/verification',        icon:ShieldCheck,     roles:SELLER_ROLES,
    children:[
      { label:'Identity verification', href:'/verification', icon:UserCheck },
      { label:'Land title',            href:'/ownership',    icon:Landmark },
    ] },
  { label:'Become an Agent', href:'/agent-application', icon:Briefcase,      roles:['SELLER'] },
  { label:'Viewings',     href:'/viewings',            icon:Calendar,        roles:['BUYER',...SELLER_ROLES],
    children:[
      { label:'As buyer',  href:'/viewings?tab=buyer',  roles:SELLER_ROLES, icon:Home },
      { label:'As seller', href:'/viewings?tab=seller', roles:SELLER_ROLES, icon:Store },
    ] },
  { label:'Payments',     href:'/payments',            icon:CreditCard,      roles:['BUYER',...SELLER_ROLES] },
  { label:'Reviews',      href:'/reviews',             icon:Star,            roles:['BUYER',...SELLER_ROLES] },
]
const adminNav: NavItem[] = [
  { label:'Command Center', href:'/overview',          icon:Gauge },
  { label:'Revenue',      href:'/revenue',             icon:BarChart3,
    children:[
      { label:'Overview',       href:'/revenue',                icon:PieChart },
      { label:'Pending escrow', href:'/revenue#pending-release', icon:Clock },
      { label:'Ledger',         href:'/revenue#ledger',          icon:Receipt },
    ] },
  { label:'Users',        href:'/users',               icon:Users,
    children:[
      { label:'All users', href:'/users',              icon:Users },
      { label:'Sellers',   href:'/users?role=SELLER',  icon:Store },
      { label:'Agents',    href:'/users?role=AGENT',   icon:Briefcase },
      { label:'Buyers',    href:'/users?role=BUYER',   icon:Home },
    ] },
  { label:'Verif Queue',  href:'/verification-queue',  icon:ListChecks,
    children:[
      { label:'Identity queue',  href:'/verification-queue?tab=identity',  icon:ShieldCheck },
      { label:'Ownership queue', href:'/verification-queue?tab=ownership', icon:Landmark },
    ] },
  { label:'Listings',     href:'/manage-listings',     icon:Building2,
    children:[
      { label:'All',                  href:'/manage-listings',                          icon:List },
      { label:'Active',               href:'/manage-listings?status=ACTIVE',            icon:CheckCircle },
      { label:'Pending verification', href:'/manage-listings?status=PENDING_VERIFICATION', icon:Clock },
      { label:'Draft',                href:'/manage-listings?status=DRAFT',             icon:FileEdit },
      { label:'Suspended',            href:'/manage-listings?status=SUSPENDED',         icon:Ban },
    ] },
  { label:'Reports',      href:'/reports',             icon:Flag,
    children:[
      { label:'Open',      href:'/reports?status=OPEN',      icon:AlertCircle },
      { label:'Resolved',  href:'/reports?status=RESOLVED',  icon:CheckCircle },
      { label:'Dismissed', href:'/reports?status=DISMISSED', icon:XCircle },
    ] },
  { label:'Agent Apps',   href:'/agent-applications',  icon:Briefcase,
    children:[
      { label:'Submitted', href:'/agent-applications?status=SUBMITTED', icon:Clock },
      { label:'Approved',  href:'/agent-applications?status=APPROVED',  icon:CheckCircle },
      { label:'Rejected',  href:'/agent-applications?status=REJECTED',  icon:XCircle },
    ] },
  { label:'Reviews',      href:'/reviews',             icon:Star,
    children:[
      { label:'All',      href:'/reviews',              icon:List },
      { label:'Hidden',   href:'/reviews?visible=false', icon:EyeOff },
    ] },
]

function parseHref(href: string) {
  const hashIdx = href.indexOf('#')
  const hash = hashIdx >= 0 ? href.slice(hashIdx + 1) : ''
  const withoutHash = hashIdx >= 0 ? href.slice(0, hashIdx) : href
  const [pathname, query = ''] = withoutHash.split('?')
  return { pathname, hash, params: new URLSearchParams(query) }
}

function autoExpanded(path: string) {
  const labels = new Set<string>()
  for (const item of [...nav, ...adminNav]) {
    if (item.children && (item.href === '/' ? path === '/' : path.startsWith(item.href))) labels.add(item.label)
  }
  return labels
}

export default function Sidebar({ open, onClose }:{ open:boolean; onClose():void }) {
  const path = usePathname()
  const searchParams = useSearchParams()
  const user = useAuthStore(s => s.user)
  const collapsed = useUIStore(s => s.collapsed)
  const toggleCollapsed = useUIStore(s => s.toggleCollapsed)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const rail = isDesktop && collapsed
  const [hash, setHash] = useState('')
  const [expanded, setExpanded] = useState<Set<string>>(() => autoExpanded(path))

  useEffect(() => { setExpanded(autoExpanded(path)) }, [path])
  useEffect(() => {
    const update = () => setHash(window.location.hash.replace('#', ''))
    update()
    window.addEventListener('hashchange', update)
    return () => window.removeEventListener('hashchange', update)
  }, [path])

  const toggleGroup = (label: string) => setExpanded(prev => {
    const next = new Set(prev)
    next.has(label) ? next.delete(label) : next.add(label)
    return next
  })

  const isActive = (href: string) => href === '/' ? path === '/' : path.startsWith(href)
  const isChildActive = (siblings: Child[], child: Child) => {
    const parsed = parseHref(child.href)
    if (path !== parsed.pathname) return false
    const allKeys = siblings.flatMap(c => Array.from(parseHref(c.href).params.keys()))
    const paramKeys = allKeys.filter((k, i) => allKeys.indexOf(k) === i)
    for (const key of paramKeys) {
      if ((searchParams.get(key) ?? '') !== (parsed.params.get(key) ?? '')) return false
    }
    const usesHash = siblings.some(c => parseHref(c.href).hash)
    if (usesHash && hash !== parsed.hash) return false
    return true
  }
  const visible = nav.filter(n => !user || n.roles!.includes(user.role))
  const visibleChildren = (item: NavItem) => (item.children ?? []).filter(c => !c.roles || !user || c.roles.includes(user.role))

  const NavItem = ({ item }: { item: NavItem }) => {
    const active = isActive(item.href)
    const children = visibleChildren(item)
    const hasChildren = children.length > 0 && !rail
    const isOpen = expanded.has(item.label)
    return (
      <div>
        <div className={cn('relative group flex items-center', active ? 'sidebar-link-active' : 'sidebar-link', rail && 'justify-center px-0 gap-0', '!p-0')}>
          {active && <span className="absolute left-0 top-1/4 bottom-1/4 w-0.5 bg-gold-500 rounded-r" />}
          <Link href={item.href} onClick={onClose}
            className={cn('flex-1 flex items-center gap-3 px-3 py-2 min-w-0', rail && 'justify-center px-0 gap-0')}>
            <item.icon size={17} className="shrink-0"/>
            {!rail && <span className="truncate">{item.label}</span>}
          </Link>
          {hasChildren && (
            <button type="button" onClick={() => toggleGroup(item.label)} aria-label={isOpen ? `Collapse ${item.label}` : `Expand ${item.label}`}
              className="shrink-0 p-2 mr-1 rounded-md hover:bg-black/5 dark:hover:bg-white/10 text-gray-400 transition-colors">
              <ChevronDown size={14} className={cn('transition-transform duration-150', isOpen && 'rotate-180')}/>
            </button>
          )}
          {rail && (
            <span className="pointer-events-none absolute left-full ml-3 whitespace-nowrap rounded-md bg-gray-900 dark:bg-black px-2.5 py-1.5 text-[12px] font-medium text-white opacity-0 scale-95 origin-left group-hover:opacity-100 group-hover:scale-100 transition-all duration-150 z-50 shadow-lg">
              {item.label}
            </span>
          )}
        </div>
        {hasChildren && isOpen && (
          <div className="ml-[26px] pl-3 border-l border-base space-y-0.5 my-0.5">
            {children.map(child => (
              <Link key={child.href} href={child.href} onClick={onClose}
                className={cn('flex items-center gap-2 px-3 py-1.5 rounded-md text-[12.5px] transition-colors truncate',
                  isChildActive(children, child)
                    ? 'text-gold-600 dark:text-gold-400 font-medium bg-gold-50 dark:bg-gold-500/10'
                    : 'text-muted hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-white/5')}>
                {child.icon && <child.icon size={13} className="shrink-0"/>}
                <span className="truncate">{child.label}</span>
              </Link>
            ))}
          </div>
        )}
      </div>
    )
  }

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
