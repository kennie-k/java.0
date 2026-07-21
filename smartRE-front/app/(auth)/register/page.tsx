import { Suspense } from 'react'
import type { Metadata } from 'next'
import RegisterForm from './RegisterForm'

export const metadata: Metadata = {
  title: 'Create an account',
  description: 'Join SmartRE Kenya. Buy or rent verified property, or list your own for free and pay only 2.5% when a sale closes.',
  alternates: { canonical: '/register' },
}

export default function RegisterPage() {
  return <Suspense fallback={null}><RegisterForm/></Suspense>
}
