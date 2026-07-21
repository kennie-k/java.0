'use client'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary'|'secondary'|'ghost'|'danger'|'outline'
type Size = 'sm'|'md'|'lg'

const V: Record<Variant,string> = {
  primary:  'btn-primary',
  secondary:'btn-secondary',
  ghost:    'btn-ghost',
  danger:   'btn-danger',
  outline:  'inline-flex items-center justify-center gap-1.5 h-8 px-3 rounded-md border border-gold-500 text-gold-500 hover:bg-gold-50 dark:hover:bg-gold-500/10 text-[13px] font-medium transition-all duration-150',
}
const S: Record<Size,string> = { sm:'!h-7 !px-2.5 !text-[11px]', md:'', lg:'!h-10 !px-5 !text-sm' }

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant; size?: Size; loading?: boolean; fullWidth?: boolean
  leftIcon?: ReactNode; rightIcon?: ReactNode
}

export default function Button({ variant='primary', size='md', loading, fullWidth, leftIcon, rightIcon, className, children, disabled, ...p }: Props) {
  return (
    <button className={cn(V[variant], S[size], fullWidth && 'w-full', className)} disabled={disabled||loading} {...p}>
      {loading ? <Loader2 size={15} className="animate-spin" /> : leftIcon}
      {children}
      {!loading && rightIcon}
    </button>
  )
}
