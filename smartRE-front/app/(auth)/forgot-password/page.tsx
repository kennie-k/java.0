import type { Metadata } from 'next'
import ForgotPasswordForm from './ForgotPasswordForm'

export const metadata: Metadata = {
  title: 'Forgot password',
  description: 'Reset your SmartRE account password.',
  alternates: { canonical: '/forgot-password' },
}

export default function ForgotPasswordPage() {
  return <ForgotPasswordForm/>
}
