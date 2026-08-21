'use client'
import { useEffect } from 'react'
import { installGlobalErrorTracking } from '@/lib/errorLogger'

// Mounted once in the root layout. Installs the window-level error/rejection
// hooks so anything that never hits a React error boundary (event handler
// throws swallowed by the browser, background timers, etc.) still gets
// funneled through lib/errorLogger.ts instead of vanishing silently.
export default function ErrorTracking() {
  useEffect(() => {
    installGlobalErrorTracking()
  }, [])
  return null
}
