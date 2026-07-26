'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Flag } from 'lucide-react'
import { reportApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import Button from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import { cn } from '@/lib/utils'
import toast from 'react-hot-toast'
import type { ReportReason, ReportTargetType } from '@/types'

const REASONS: Record<ReportTargetType, { value: ReportReason; label: string }[]> = {
  LISTING: [
    { value: 'FAKE_LISTING', label: 'Fake or misleading listing' },
    { value: 'DUPLICATE_LISTING', label: 'Duplicate of another listing' },
    { value: 'OFF_PLATFORM_PAYMENT_REQUEST', label: 'Asked me to pay outside the app' },
    { value: 'OTHER', label: 'Other' },
  ],
  USER: [
    { value: 'SCAM_AGENT', label: 'Suspected scam agent or seller' },
    { value: 'OFF_PLATFORM_PAYMENT_REQUEST', label: 'Asked me to pay outside the app' },
    { value: 'OTHER', label: 'Other' },
  ],
  REVIEW: [
    { value: 'FAKE_REVIEW', label: 'Fake or fraudulent review' },
    { value: 'OTHER', label: 'Other' },
  ],
}

interface Props {
  targetType: ReportTargetType
  targetId: string
  label?: string
  className?: string
  defaultReason?: ReportReason
}

export default function ReportButton({ targetType, targetId, label = 'Report', className, defaultReason }: Props) {
  const { user } = useAuthStore()
  const router = useRouter()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState(defaultReason || '')
  const [details, setDetails] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const openModal = () => {
    if (!user) { router.push(`/login?returnTo=${encodeURIComponent(window.location.pathname)}`); return }
    setReason(defaultReason || '')
    setDetails('')
    setOpen(true)
  }

  const submit = async () => {
    if (!reason) { toast.error('Select a reason'); return }
    setSubmitting(true)
    try {
      await reportApi.create({ targetType, targetId, reason, details: details || undefined })
      toast.success('Report submitted. Our trust & safety team will review it.')
      setOpen(false)
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to submit report')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <button type="button" onClick={openModal}
        className={cn('inline-flex items-center gap-1 text-[11px] text-muted hover:text-red-500 transition-colors', className)}>
        <Flag size={11}/>{label}
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title="Report a problem" size="sm"
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          <Button variant="danger" onClick={submit} loading={submitting}>Submit report</Button>
        </>}>
        <div className="space-y-4">
          <Select label="Reason" required options={REASONS[targetType]} value={reason} onChange={e => setReason(e.target.value)}/>
          <Textarea label="Details (optional)" placeholder="Anything else we should know?" value={details} onChange={e => setDetails(e.target.value)}/>
        </div>
      </Modal>
    </>
  )
}
