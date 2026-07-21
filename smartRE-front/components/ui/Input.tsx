'use client'
import { forwardRef, InputHTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label?: string; error?: string; hint?: string
  leftIcon?: ReactNode; rightIcon?: ReactNode
}

const Input = forwardRef<HTMLInputElement, Props>(({ label, error, hint, leftIcon, rightIcon, className, ...p }, ref) => (
  <div className="flex flex-col gap-1">
    {label && <label className="text-[11px] font-medium text-gray-600 dark:text-gray-400">{label}{p.required && <span className="text-gold-500 ml-0.5">*</span>}</label>}
    <div className="relative">
      {leftIcon && <span className="absolute left-0 top-0 bottom-0 w-8 flex items-center justify-center text-gray-400 pointer-events-none">{leftIcon}</span>}
      <input ref={ref} className={cn('input-base', leftIcon && '!pl-8', rightIcon && '!pr-8', error && '!border-red-400 focus:!ring-red-400/20', className)} {...p} />
      {rightIcon && <span className="absolute right-0 top-0 bottom-0 w-8 flex items-center justify-center text-gray-400">{rightIcon}</span>}
    </div>
    {(error || hint) && <p className={cn('text-[11px]', error ? 'text-red-500' : 'text-gray-400')}>{error || hint}</p>}
  </div>
))
Input.displayName = 'Input'
export default Input
