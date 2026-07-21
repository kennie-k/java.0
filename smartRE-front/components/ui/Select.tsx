'use client'
import { forwardRef, SelectHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface Props extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string; error?: string; options: {value:string;label:string}[]
}

const Select = forwardRef<HTMLSelectElement, Props>(({ label, error, options, className, ...p }, ref) => (
  <div className="flex flex-col gap-1.5">
    {label && <label className="text-xs font-medium text-gray-600 dark:text-gray-400">{label}{p.required && <span className="text-gold-500 ml-0.5">*</span>}</label>}
    <select ref={ref} className={cn('input-base !pr-8 cursor-pointer', error && '!border-red-400', className)} {...p}>
      <option value="">Select {label}...</option>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
    {error && <p className="text-xs text-red-500">{error}</p>}
  </div>
))
Select.displayName = 'Select'
export default Select
