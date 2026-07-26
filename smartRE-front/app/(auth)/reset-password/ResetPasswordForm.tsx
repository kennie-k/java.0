'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Eye, EyeOff, Lock, ArrowLeft, ShieldAlert } from 'lucide-react'
import { authApi } from '@/lib/api'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import toast from 'react-hot-toast'

const schema = z.object({ password: z.string().min(8,'Password must be at least 8 characters') })
type Form = z.infer<typeof schema>

export default function ResetPasswordForm() {
  const [showPwd, setShow] = useState(false)
  const router = useRouter()
  const sp = useSearchParams()
  const token = sp.get('token')
  const { register, handleSubmit, formState:{ errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = async (d: Form) => {
    if (!token) return
    try {
      await authApi.resetPassword(token, d.password)
      toast.success('Password reset. Please sign in.')
      router.push('/login')
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'This reset link is invalid or has expired')
    }
  }

  if (!token) {
    return (
      <Card padding="sm" className="sm:p-6 text-center">
        <div className="w-12 h-12 rounded-full bg-red-50 dark:bg-red-500/10 text-red-600 flex items-center justify-center mx-auto mb-4">
          <ShieldAlert size={22}/>
        </div>
        <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Invalid reset link</h1>
        <p className="text-[13px] text-muted mt-2">This password reset link is missing or malformed. Request a new one below.</p>
        <Link href="/forgot-password" className="inline-flex items-center gap-1.5 text-[13px] font-medium text-gold-600 dark:text-gold-400 hover:underline mt-5">
          <ArrowLeft size={14}/>Request a new link
        </Link>
      </Card>
    )
  }

  return (
    <Card padding="sm" className="sm:p-6">
      <div className="mb-5">
        <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Choose a new password</h1>
        <p className="text-[13px] text-muted mt-1">Make it at least 8 characters.</p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
        <Input label="New password" type={showPwd?'text':'password'} placeholder="••••••••" required
          leftIcon={<Lock size={15}/>}
          rightIcon={<button type="button" onClick={() => setShow(s=>!s)} className="text-gray-400 hover:text-gray-600" aria-label={showPwd ? 'Hide password' : 'Show password'}>{showPwd?<EyeOff size={15}/>:<Eye size={15}/>}</button>}
          {...register('password')} error={errors.password?.message}/>
        <Button type="submit" fullWidth loading={isSubmitting}>Reset password</Button>
      </form>
      <Link href="/login" className="inline-flex items-center gap-1.5 text-[13px] font-medium text-gold-600 dark:text-gold-400 hover:underline mt-4">
        <ArrowLeft size={14}/>Back to sign in
      </Link>
    </Card>
  )
}
