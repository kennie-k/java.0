'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Eye, EyeOff, Mail, Lock, Building2, ArrowRight } from 'lucide-react'
import { authApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import toast from 'react-hot-toast'

const schema = z.object({ email: z.string().email('Invalid email'), password: z.string().min(1,'Required') })
type Form = z.infer<typeof schema>

export default function LoginForm() {
  const [showPwd, setShow] = useState(false)
  const { setUser } = useAuthStore()
  const router = useRouter()
  const sp = useSearchParams()
  const { register, handleSubmit, formState:{ errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = async (d: Form) => {
    try {
      const res = await authApi.login(d)
      setUser(res)
      toast.success(`Welcome back, ${res.fullName.split(' ')[0]}!`)
      const returnTo = sp.get('returnTo')
      router.push(returnTo || (res.role === 'ADMIN' ? '/overview' : '/dashboard'))
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Invalid email or password')
    }
  }

  return (
    <>
      <Card padding="sm" className="sm:p-6">
        <div className="mb-5">
          <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Sign in to your account</h1>
          <p className="text-[13px] text-muted mt-1">Continue to schedule viewings, pay securely, and manage your listings.</p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
          <Input label="Email address" type="email" placeholder="kennieme24@gmail.com" required
            leftIcon={<Mail size={15}/>} {...register('email')} error={errors.email?.message}/>
          <Input label="Password" type={showPwd?'text':'password'} placeholder="••••••••" required
            leftIcon={<Lock size={15}/>}
            rightIcon={<button type="button" onClick={() => setShow(s=>!s)} className="text-gray-400 hover:text-gray-600" aria-label={showPwd ? 'Hide password' : 'Show password'}>{showPwd?<EyeOff size={15}/>:<Eye size={15}/>}</button>}
            {...register('password')} error={errors.password?.message}/>
          <div className="flex justify-end -mt-1.5">
            <Link href="/forgot-password" className="text-[12px] font-medium text-gold-600 dark:text-gold-400 hover:underline">Forgot password?</Link>
          </div>
          <Button type="submit" fullWidth loading={isSubmitting}>Sign in</Button>
        </form>
      </Card>

      <Link href="/register?role=SELLER" className="mt-4 flex items-center gap-3 p-3.5 rounded-xl border border-dashed border-gold-300 dark:border-gold-500/30 bg-gold-50/50 dark:bg-gold-500/5 hover:bg-gold-50 dark:hover:bg-gold-500/10 transition-colors group">
        <div className="w-9 h-9 rounded-lg bg-gold-100 dark:bg-gold-500/15 text-gold-600 dark:text-gold-400 flex items-center justify-center shrink-0">
          <Building2 size={16}/>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-[13px] font-semibold text-gray-900 dark:text-white">New here and want to sell a property?</p>
          <p className="text-[11px] text-muted">Create a seller account. Listing is free, and you only pay 2.5% on a closed sale.</p>
        </div>
        <ArrowRight size={15} className="text-gold-500 shrink-0 group-hover:translate-x-0.5 transition-transform"/>
      </Link>
    </>
  )
}
