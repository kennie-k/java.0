'use client'
import { useEffect, useState } from 'react'
import { Briefcase, CheckCircle, Clock, XCircle, ShieldAlert } from 'lucide-react'
import { agentApplicationApi, verifApi } from '@/lib/api'
import type { AgentApplicationResponse, IdentityVerificationResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import FileUpload from '@/components/ui/FileUpload'
import { StatusBadge } from '@/components/ui/Badge'
import { PageLoader } from '@/components/ui/Modal'
import { cn } from '@/lib/utils'
import toast from 'react-hot-toast'

const STATUS_INFO: Record<string, { icon: any; color: string; title: string; desc: string }> = {
  SUBMITTED: { icon: Clock, color: 'text-amber-500', title: 'Under review', desc: 'Our team is reviewing your application. This takes 1-2 business days.' },
  APPROVED: { icon: CheckCircle, color: 'text-emerald-500', title: 'Approved', desc: 'You are now an approved agent on SmartRE.' },
  REJECTED: { icon: XCircle, color: 'text-red-500', title: 'Rejected', desc: 'Your application was rejected. See reason below and reapply.' },
}

export default function AgentApplicationPage() {
  const [identity, setIdentity] = useState<IdentityVerificationResponse | null>(null)
  const [application, setApplication] = useState<AgentApplicationResponse | null>(null)
  const [loading, setLoad] = useState(true)
  const [businessName, setBusinessName] = useState('')
  const [businessDocUrl, setBusinessDocUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    Promise.allSettled([verifApi.myId(), agentApplicationApi.mine()]).then(([id, app]) => {
      if (id.status === 'fulfilled') setIdentity(id.value)
      if (app.status === 'fulfilled') setApplication(app.value)
    }).finally(() => setLoad(false))
  }, [])

  const submit = async () => {
    if (!businessDocUrl) { toast.error('Upload your business registration document'); return }
    setSubmitting(true)
    try {
      const app = await agentApplicationApi.submit({ businessName: businessName || undefined, businessDocUrl })
      setApplication(app)
      toast.success('Application submitted! We\'ll review within 1-2 business days.')
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to submit application')
    } finally { setSubmitting(false) }
  }

  if (loading) return <PageLoader/>

  const identityApproved = identity?.status === 'APPROVED' && !identity.expired

  if (!identityApproved) {
    return (
      <div className="max-w-3xl mx-auto space-y-6">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Become an Agent</h1>
          <p className="text-muted text-sm mt-1">Agent accreditation for professional sellers and brokers</p>
        </div>
        <Card>
          <div className="text-center py-6">
            <ShieldAlert size={40} className="mx-auto text-amber-400 mb-4"/>
            <h3 className="font-display font-semibold text-lg mb-2">Identity verification required first</h3>
            <p className="text-sm text-muted max-w-sm mx-auto">
              Complete identity verification before applying to become an agent. This confirms who you are before we review your business credentials.
            </p>
          </div>
        </Card>
      </div>
    )
  }

  const status = application?.status
  const info = status ? STATUS_INFO[status] : null

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Become an Agent</h1>
        <p className="text-muted text-sm mt-1">Agent accreditation for professional sellers and brokers</p>
      </div>

      {info && (
        <Card>
          <div className="flex items-center gap-4">
            <div className={cn('w-14 h-14 rounded-2xl flex items-center justify-center shrink-0', status === 'APPROVED' ? 'bg-emerald-100 dark:bg-emerald-500/15' : status === 'REJECTED' ? 'bg-red-50 dark:bg-red-500/10' : 'bg-gold-50 dark:bg-gold-500/10')}>
              <info.icon size={28} className={info.color}/>
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <h2 className="font-display font-bold text-lg text-gray-900 dark:text-white">{info.title}</h2>
                <StatusBadge status={status!} size="sm"/>
              </div>
              <p className="text-sm text-muted">{info.desc}</p>
            </div>
          </div>
          {application?.rejectionReason && (
            <div className="mt-4 p-3 bg-red-50 dark:bg-red-500/10 rounded-lg border border-red-200 dark:border-red-500/20">
              <p className="text-sm text-red-600 dark:text-red-400"><strong>Reason:</strong> {application.rejectionReason}</p>
            </div>
          )}
        </Card>
      )}

      {(!application || application.status === 'REJECTED') && (
        <Card>
          <h2 className="font-display font-semibold text-[14px] mb-4 flex items-center gap-2"><Briefcase size={17} className="text-gold-500"/>Apply as an agent</h2>
          <div className="space-y-4">
            <Input label="Business or agency name (optional)" placeholder="e.g. Acme Realty Ltd" value={businessName} onChange={e => setBusinessName(e.target.value)}/>
            <div>
              <label className="text-xs font-medium text-gray-600 dark:text-gray-400 block mb-2">Business registration document</label>
              {businessDocUrl ? (
                <div className="flex items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
                  <CheckCircle size={15}/>Document uploaded
                </div>
              ) : (
                <FileUpload category="AGENT_BUSINESS_DOCUMENT" compact label="Upload your business permit or registration certificate" onUploaded={setBusinessDocUrl}/>
              )}
            </div>
            <Button onClick={submit} loading={submitting} disabled={!businessDocUrl}>Submit application</Button>
          </div>
        </Card>
      )}
    </div>
  )
}
