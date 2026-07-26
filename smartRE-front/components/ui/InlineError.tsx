'use client'
import { AlertCircle } from 'lucide-react'

export function InlineError({ message = 'Something went wrong loading this data.' }: { message?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-500/10 rounded-lg p-3 mb-4">
      <AlertCircle size={15} className="shrink-0"/>
      <span>{message}</span>
    </div>
  )
}
