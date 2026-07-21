import { Suspense } from 'react'
import type { Metadata } from 'next'
import LoginForm from './LoginForm'

export const metadata: Metadata = {
  title: 'Sign in',
  description: 'Sign in to SmartRE to schedule property viewings, pay securely via M-Pesa, and manage your listings.',
  alternates: { canonical: '/login' },
}

export default function LoginPage() {
  return <Suspense fallback={null}><LoginForm/></Suspense>
}
