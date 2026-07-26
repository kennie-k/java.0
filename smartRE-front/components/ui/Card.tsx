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
  const accent: Record<string,string> = {
    gold:'border-l-gold-400 dark:border-l-gold-500/60',
    emerald:'border-l-emerald-400 dark:border-l-emerald-500/60',
    blue:'border-l-blue-400 dark:border-l-blue-500/60',
    purple:'border-l-purple-400 dark:border-l-purple-500/60',
  }
  const iconColor: Record<string,string> = {
    gold:'text-gold-500', emerald:'text-emerald-500', blue:'text-blue-500', purple:'text-purple-500',
  }
  return (
    <Card className={cn('group relative border-l-[3px] card-hover', accent[color])}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] font-medium text-muted mb-1.5 truncate">{label}</p>
          <p className="text-[22px] font-semibold text-gray-900 dark:text-white leading-none tracking-tight tabular-nums truncate">{value}</p>
          {sub && <p className="text-[11px] text-muted mt-2 truncate">{sub}</p>}
        </div>
        {icon && (
          <div className={cn('w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-gray-50 dark:bg-white/5 transition-transform duration-200 group-hover:scale-110', iconColor[color])}>
            {icon}
          </div>
        )}
      </div>
    </Card>
  )
}
