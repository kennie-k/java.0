'use client'
import { cn } from '@/lib/utils'
import { ReactNode, HTMLAttributes } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> { hover?: boolean; padding?: 'none'|'sm'|'md'|'lg' }
const pads = { none:'p-0', sm:'p-3', md:'p-4', lg:'p-6' }

export function Card({ hover, padding='md', className, children, ...p }: CardProps) {
  return <div className={cn('card', pads[padding], hover && 'card-hover cursor-pointer', className)} {...p}>{children}</div>
}

export function StatCard({ label, value, sub, icon, color='gold' }:
  { label:string; value:ReactNode; sub?:ReactNode; icon?:ReactNode; color?:'gold'|'emerald'|'blue'|'purple' }) {
  const colors: Record<string,string> = {
    gold:'bg-gold-100 text-gold-600 dark:bg-gold-500/15 dark:text-gold-400',
    emerald:'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400',
    blue:  'bg-blue-100 text-blue-600 dark:bg-blue-500/15 dark:text-blue-400',
    purple:'bg-purple-100 text-purple-600 dark:bg-purple-500/15 dark:text-purple-400',
  }
  return (
    <Card>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-[11px] text-muted mb-0.5 truncate">{label}</p>
          <p className="font-display text-lg font-bold text-gray-900 dark:text-white leading-tight truncate">{value}</p>
          {sub && <p className="text-[10px] text-muted mt-0.5 truncate">{sub}</p>}
        </div>
        {icon && <div className={cn('w-7 h-7 rounded-md flex items-center justify-center flex-shrink-0', colors[color])}>{icon}</div>}
      </div>
    </Card>
  )
}
