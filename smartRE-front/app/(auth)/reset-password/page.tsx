import { Suspense } from 'react'
import type { Metadata } from 'next'
import ResetPasswordForm from './ResetPasswordForm'

export const metadata: Metadata = {
  title: 'Reset password',
  description: 'Choose a new password for your SmartRE account.',
  alternates: { canonical: '/reset-password' },
}

export default function ResetPasswordPage() {
  return <Suspense fallback={null}><ResetPasswordForm/></Suspense>
}
