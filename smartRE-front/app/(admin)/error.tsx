'use client'
import { useEffect } from 'react'
import { AlertTriangle, RotateCcw } from 'lucide-react'
import { reportError } from '@/lib/errorLogger'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'

// Segment-level error boundary for the admin area — see the matching
// app/(dashboard)/error.tsx for why this lives here rather than relying on
// the root app/error.tsx: it keeps app/(admin)/layout.tsx's Sidebar/Topbar
// mounted around the failure instead of losing the whole admin chrome.
export default function AdminError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    reportError(error, 'react-error-boundary', { boundary: 'admin' })
  }, [error])

  const isDev = process.env.NODE_ENV === 'development'

  return (
    <div className="flex items-center justify-center py-16 px-4">
      <Card className="max-w-md w-full text-center">
        <div className="w-12 h-12 rounded-2xl bg-red-50 dark:bg-red-500/10 flex items-center justify-center mx-auto mb-4">
          <AlertTriangle size={22} className="text-red-500"/>
        </div>
        <h2 className="font-display text-base font-semibold text-gray-900 dark:text-white mb-1.5">This page hit a snag</h2>
        <p className="text-sm text-muted mb-5">
          Try again — the rest of the admin console is unaffected.
        </p>
        {isDev && (
          <div className="text-left w-full mb-5 p-3 rounded-lg bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 overflow-auto">
            <p className="text-[11px] font-mono font-semibold text-red-700 dark:text-red-400">{error.name}: {error.message}</p>
          </div>
        )}
        <Button onClick={reset} leftIcon={<RotateCcw size={15}/>}>Try again</Button>
      </Card>
    </div>
  )
}
