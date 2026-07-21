'use client'
import { forwardRef, TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface Props extends TextareaHTMLAttributes<HTMLTextAreaElement> { label?: string; error?: string }
const Textarea = forwardRef<HTMLTextAreaElement, Props>(({ label, error, className, ...p }, ref) => (
  <div className="flex flex-col gap-1.5">
    {label && <label className="text-xs font-medium text-gray-600 dark:text-gray-400">{label}</label>}
    <textarea ref={ref} className={cn('input-base !h-auto py-2.5 resize-none', error && '!border-red-400', className)} rows={4} {...p} />
    {error && <p className="text-xs text-red-500">{error}</p>}
  </div>
))
Textarea.displayName = 'Textarea'
export default Textarea
