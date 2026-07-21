import Link from 'next/link'
import { Home, Search } from 'lucide-react'

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 text-center bg-surface">
      <span className="font-display text-6xl font-bold text-gold-500 mb-2">404</span>
      <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white mb-2">Page not found</h1>
      <p className="text-sm text-muted max-w-sm mb-6">
        The page you&apos;re looking for doesn&apos;t exist, or the listing may have been sold, rented, or removed.
      </p>
      <div className="flex gap-3">
        <Link href="/" className="btn-secondary"><Home size={15}/>Go home</Link>
        <Link href="/properties" className="btn-primary"><Search size={15}/>Browse listings</Link>
      </div>
    </div>
  )
}
