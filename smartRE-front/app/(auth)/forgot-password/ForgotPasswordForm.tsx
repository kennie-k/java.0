'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Mail, ArrowLeft, MailCheck } from 'lucide-react'
import { authApi } from '@/lib/api'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import toast from 'react-hot-toast'

const schema = z.object({ email: z.string().email('Invalid email') })
type Form = z.infer<typeof schema>

export default function ForgotPasswordForm() {
  const [sent, setSent] = useState(false)
  const { register, handleSubmit, formState:{ errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = async (d: Form) => {
    try {
      await authApi.forgotPassword(d.email)
      setSent(true)
    } catch {
      toast.error('Something went wrong. Please try again.')
    }
  }

  if (sent) {
    return (
      <Card padding="sm" className="sm:p-6 text-center">
        <div className="w-12 h-12 rounded-full bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 flex items-center justify-center mx-auto mb-4">
          <MailCheck size={22}/>
        </div>
        <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Check your email</h1>
        <p className="text-[13px] text-muted mt-2">If an account exists for that email, we&apos;ve sent a link to reset your password. It expires in 30 minutes.</p>
        <Link href="/login" className="inline-flex items-center gap-1.5 text-[13px] font-medium text-gold-600 dark:text-gold-400 hover:underline mt-5">
          <ArrowLeft size={14}/>Back to sign in
        </Link>
      </Card>
    )
  }

  return (
    <Card padding="sm" className="sm:p-6">
      <div className="mb-5">
        <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Reset your password</h1>
        <p className="text-[13px] text-muted mt-1">Enter the email you signed up with and we&apos;ll send you a reset link.</p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
        <Input label="Email address" type="email" placeholder="kennieme24@gmail.com" required
          leftIcon={<Mail size={15}/>} {...register('email')} error={errors.email?.message}/>
        <Button type="submit" fullWidth loading={isSubmitting}>Send reset link</Button>
      </form>
      <Link href="/login" className="inline-flex items-center gap-1.5 text-[13px] font-medium text-gold-600 dark:text-gold-400 hover:underline mt-4">
        <ArrowLeft size={14}/>Back to sign in
      </Link>
    </Card>
  )
}
