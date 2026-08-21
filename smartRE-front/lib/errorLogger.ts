// Centralized client-side error reporting.
//
// There is no external error-tracking service (e.g. Sentry) wired up in this
// environment yet, so this module is the single choke point every uncaught
// error / rejection / boundary-caught error flows through. It logs a
// structured event to the console today; when a Sentry (or similar) DSN
// becomes available, point `report()` at it and every call site — the React
// error boundaries, the window-level hooks in ErrorTracking.tsx, and any
// manual `reportError(...)` calls — starts shipping there with no other
// changes required.

export type ErrorSource = 'window.onerror' | 'unhandledrejection' | 'react-error-boundary' | 'manual'

export interface ReportedError {
  message: string
  stack?: string
  source: ErrorSource
  digest?: string
  url?: string
  userAgent?: string
  timestamp: string
  extra?: Record<string, unknown>
}

function toReportedError(error: unknown, source: ErrorSource, extra?: Record<string, unknown>): ReportedError {
  const err = error instanceof Error ? error : new Error(typeof error === 'string' ? error : JSON.stringify(error))
  return {
    message: err.message,
    stack: err.stack,
    source,
    digest: (err as Error & { digest?: string }).digest,
    url: typeof window !== 'undefined' ? window.location.href : undefined,
    userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
    timestamp: new Date().toISOString(),
    extra,
  }
}

// The actual "sink". Swap this implementation for `Sentry.captureException` /
// a `/api/client-errors` beacon / etc. once a real backend for it exists —
// every caller below goes through here, so that's the only edit needed.
function sink(event: ReportedError) {
  // eslint-disable-next-line no-console
  console.error(`[${event.source}]`, event.message, event)
}

export function reportError(error: unknown, source: ErrorSource = 'manual', extra?: Record<string, unknown>) {
  try {
    sink(toReportedError(error, source, extra))
  } catch {
    // Reporting must never itself throw and break the app.
  }
}

let installed = false

// Wires window.onerror / unhandledrejection into the same reporting path as
// React error boundaries. Safe to call multiple times — only installs once.
export function installGlobalErrorTracking() {
  if (installed || typeof window === 'undefined') return
  installed = true

  window.addEventListener('error', event => {
    reportError(event.error ?? event.message, 'window.onerror', {
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
    })
  })

  window.addEventListener('unhandledrejection', event => {
    reportError(event.reason, 'unhandledrejection')
  })
}
