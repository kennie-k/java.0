'use client'
import { cn, statusVariant, statusLabel } from '@/lib/utils'
import { ShieldCheck, ShieldX, Shield, ShieldAlert, Star } from 'lucide-react'

type Variant = 'success'|'warning'|'error'|'info'|'muted'|'gold'

const styles: Record<Variant,string> = {
  success:'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400',
  warning:'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400',
  error:  'bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400',
  info:   'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400',
  muted:  'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  gold: 'bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400',
}

export function Badge({ variant='muted', children, dot, size='md', className }:
  { variant?:Variant; children:React.ReactNode; dot?:boolean; size?:'sm'|'md'; className?:string }) {
  return (
    <span className={cn('badge', styles[variant], size==='sm'?'text-[11px] px-2 py-0':'', className)}>
      {dot && <span className={cn('w-1.5 h-1.5 rounded-full shrink-0', {
        'bg-emerald-500':variant==='success','bg-amber-500':variant==='warning',
        'bg-red-500':variant==='error','bg-blue-500':variant==='info',
        'bg-gray-400':variant==='muted','bg-gold-500':variant==='gold',
      })} />}
      {children}
    </span>
  )
}

export function StatusBadge({ status, size }:{ status:string; size?:'sm'|'md' }) {
  return <Badge variant={statusVariant(status)} size={size}>{statusLabel(status)}</Badge>
}

export function TrustBadge({ identityVerified, ownershipVerified, fullyTrusted, size='md' }:
  { identityVerified:boolean; ownershipVerified?:boolean; fullyTrusted?:boolean; size?:'sm'|'md'|'lg' }) {
  const cls = cn('badge font-semibold', size==='sm'?'text-[11px]':size==='lg'?'text-sm px-3 py-1':'')
  if (fullyTrusted) return <span className={cn(cls,'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400')}><ShieldCheck size={size==='sm'?11:size==='lg'?16:13}/>Fully Verified</span>
  if (identityVerified && !ownershipVerified) return <span className={cn(cls,'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400')}><Shield size={13}/>Identity Only</span>
  if (identityVerified) return <span className={cn(cls,'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400')}><ShieldAlert size={13}/>Partial</span>
  return <span className={cn(cls,'bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400')}><ShieldX size={13}/>Unverified</span>
}

export function RatingStars({ rating, max=5 }:{ rating:number; max?:number }) {
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({length:max}).map((_,i) => (
        <Star key={i} size={14} className={i < Math.round(rating) ? 'text-amber-400 fill-amber-400' : 'text-gray-200 dark:text-gray-700'}/>
      ))}
    </div>
  )
}
