'use client'
import { useEffect } from 'react'

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <html lang="en">
      <body style={{ fontFamily: 'sans-serif' }}>
        <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 16, textAlign: 'center' }}>
          <h1 style={{ fontSize: 20, fontWeight: 600, marginBottom: 8 }}>SmartRE hit a snag</h1>
          <p style={{ fontSize: 14, color: '#6b7280', maxWidth: 380, marginBottom: 24 }}>
            Something went wrong loading the app itself. Reloading usually fixes this.
          </p>
          <button onClick={reset} style={{ height: 40, padding: '0 20px', borderRadius: 8, background: '#C9A227', color: '#fff', border: 'none', fontWeight: 500, cursor: 'pointer' }}>
            Reload
          </button>
        </div>
      </body>
    </html>
  )
}
