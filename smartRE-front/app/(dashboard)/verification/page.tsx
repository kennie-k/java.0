'use client'
import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ShieldCheck, CheckCircle, Clock, XCircle, AlertTriangle, ChevronRight, FileText, Upload, X } from 'lucide-react'
import { verifApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import type { IdentityVerificationResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import FileUpload from '@/components/ui/FileUpload'
import { StatusBadge } from '@/components/ui/Badge'
import { PageLoader, ConfirmModal } from '@/components/ui/Modal'
import { fmt, cn } from '@/lib/utils'
import toast from 'react-hot-toast'

const NON_TERMINAL = ['SUBMITTED', 'AI_SCREENING', 'HUMAN_REVIEW']

const DOC_CATEGORIES = [
  { key:'NATIONAL_ID_FRONT',   label:'National ID (Front)',    desc:'Clear photo of front side' },
  { key:'NATIONAL_ID_BACK',    label:'National ID (Back)',     desc:'Clear photo of back side' },
  { key:'KRA_PIN_CERTIFICATE', label:'KRA PIN Certificate',    desc:'Official KRA PIN document' },
  { key:'SELFIE_WITH_ID',      label:'Selfie with ID',         desc:'Hold your ID next to your face' },
]

const STATUS_INFO: Record<string,{ icon:any; color:string; title:string; desc:string }> = {
  DRAFT:                  { icon:Clock,        color:'text-gray-400',    title:'Not started',       desc:'Start your identity verification to list properties.' },
  SUBMITTED:              { icon:Clock,        color:'text-amber-500',   title:'Under review',      desc:'Our team is reviewing your documents. This takes 1-2 business days.' },
  AI_SCREENING:           { icon:Clock,        color:'text-amber-500',   title:'AI screening',      desc:'Our AI is analysing your documents.' },
  HUMAN_REVIEW:           { icon:Clock,        color:'text-amber-500',   title:'Human review',      desc:'A team member is reviewing your verification.' },
  APPROVED:               { icon:CheckCircle,  color:'text-emerald-500', title:'Verified',          desc:'Your identity is verified. Your listings are now active.' },
  REJECTED:               { icon:XCircle,      color:'text-red-500',     title:'Rejected',          desc:'Your verification was rejected. See reason below and resubmit.' },
  REQUIRES_RESUBMISSION:  { icon:AlertTriangle,color:'text-amber-500',   title:'Resubmission needed', desc:'Some documents need to be replaced. See notes below.' },
  EXPIRED:                { icon:AlertTriangle,color:'text-amber-500',   title:'Expired',           desc:'Your verification has expired. Please resubmit your documents.' },
}

export default function VerificationPage() {
  const router = useRouter()
  const { user, setUser } = useAuthStore()
  const [verif, setVerif]       = useState<IdentityVerificationResponse | null>(null)
  const [loading, setLoad]      = useState(true)
  const [starting, setStart]    = useState(false)
  const [submitting, setSubmit] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const prevStatus = useRef<string | null>(null)

  useEffect(() => {
    verifApi.myId().then(setVerif).catch(() => setVerif(null)).finally(() => setLoad(false))
  }, [])

  // Poll while a submission is in flight so the seller sees HUMAN_REVIEW -> APPROVED/REJECTED
  // land without having to manually refresh the page.
  useEffect(() => {
    const status = verif?.status
    if (!status || !NON_TERMINAL.includes(status)) return
    const interval = setInterval(() => {
      verifApi.myId().then(setVerif).catch(() => {})
    }, 4000)
    return () => clearInterval(interval)
  }, [verif?.status])

  useEffect(() => {
    const status = verif?.status
    if (!status) return
    const prev = prevStatus.current
    prevStatus.current = status
    if (!prev || prev === status || !NON_TERMINAL.includes(prev)) return
    if (status === 'APPROVED') {
      toast.success('Your identity verification was approved!')
      if (user) setUser({ ...user, verified: true })
    } else if (status === 'REJECTED') {
      toast.error('Your identity verification was rejected — see reason below.')
    } else if (status === 'REQUIRES_RESUBMISSION') {
      toast.error('Some documents need to be resubmitted — see notes below.')
    }
  }, [verif?.status, user, setUser])

  const start = async () => {
    setStart(true)
    try { const v = await verifApi.startId(); setVerif(v) }
    catch (e: any) { toast.error(e.response?.data?.error || 'Failed to start verification') }
    finally { setStart(false) }
  }

  const attachDoc = async (category: string, url: string) => {
    try {
      await verifApi.uploadIdDoc({ documentCategory: category, documentUrl: url })
      const v = await verifApi.myId(); setVerif(v)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to attach document') }
  }

  const submit = async () => {
    setSubmit(true)
    try {
      const v = await verifApi.submitId(); setVerif(v)
      toast.success('Verification submitted! We\'ll review within 1-2 business days.')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Submission failed') }
    finally { setSubmit(false) }
  }

  if (loading) return <PageLoader/>

  const status = verif?.status || 'DRAFT'
  const info   = STATUS_INFO[status] || STATUS_INFO.DRAFT
  const uploadedCategories = verif?.documents?.map(d => d.documentCategory) || []
  const allUploaded = DOC_CATEGORIES.every(d => uploadedCategories.includes(d.key))

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Identity Verification</h1>
        <p className="text-muted text-sm mt-1">Required to list and sell properties on SmartRE</p>
      </div>

      <Card>
        <div className="flex items-center gap-4">
          <div className={cn('w-14 h-14 rounded-2xl flex items-center justify-center shrink-0', status === 'APPROVED' ? 'bg-emerald-100 dark:bg-emerald-500/15' : status === 'REJECTED' ? 'bg-red-50 dark:bg-red-500/10' : 'bg-gold-50 dark:bg-gold-500/10')}>
            <info.icon size={28} className={info.color}/>
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <h2 className="font-display font-bold text-lg text-gray-900 dark:text-white">{info.title}</h2>
              {verif && <StatusBadge status={status} size="sm"/>}
            </div>
            <p className="text-sm text-muted">{info.desc}</p>
          </div>
          {verif && (
            <div className="text-right text-xs text-muted">
              <p>Score: <strong className="text-gray-900 dark:text-white">{verif.identityScore}/100</strong></p>
              {verif.expiresAt && <p>Expires: {fmt.date(verif.expiresAt)}</p>}
            </div>
          )}
        </div>
        {verif?.rejectionReason && (
          <div className="mt-4 p-3 bg-red-50 dark:bg-red-500/10 rounded-lg border border-red-200 dark:border-red-500/20">
            <p className="text-sm text-red-600 dark:text-red-400"><strong>Rejection reason:</strong> {verif.rejectionReason}</p>
            {verif.resubmissionNotes && <p className="text-sm text-red-600 dark:text-red-400 mt-1"><strong>Instructions:</strong> {verif.resubmissionNotes}</p>}
          </div>
        )}
        {!!verif && verif.fraudStrikeCount > 0 && (
          <div className="mt-4 p-3 bg-amber-50 dark:bg-amber-500/10 rounded-lg">
            <p className="text-sm text-amber-700 dark:text-amber-400"><AlertTriangle size={13} className="inline mr-1"/>Fraud strikes: {verif.fraudStrikeCount}/3</p>
          </div>
        )}
      </Card>

      {!verif && (
        <Card>
          <div className="text-center py-6">
            <ShieldCheck size={48} className="mx-auto text-gold-400 mb-4"/>
            <h3 className="font-display font-semibold text-lg mb-2">Start verification</h3>
            <p className="text-sm text-muted mb-6 max-w-sm mx-auto">Upload your National ID, KRA PIN certificate, and a selfie. Our system verifies your identity automatically.</p>
            <Button onClick={start} loading={starting} size="lg">Begin verification</Button>
          </div>
        </Card>
      )}

      {verif && ['DRAFT','REJECTED','EXPIRED','REQUIRES_RESUBMISSION'].includes(status) && (
        <Card>
          <h2 className="font-display font-semibold mb-4 flex items-center gap-2"><Upload size={17} className="text-gold-500"/>Upload documents</h2>
          <div className="space-y-4">
            {DOC_CATEGORIES.map(doc => {
              const uploaded = uploadedCategories.includes(doc.key)
              return (
                <div key={doc.key} className={cn('border rounded-xl p-4 transition-colors', uploaded ? 'border-emerald-200 dark:border-emerald-500/30 bg-emerald-50/50 dark:bg-emerald-500/5' : 'border-base')}>
                  <div className="flex items-start gap-3">
                    <div className={cn('w-9 h-9 rounded-lg flex items-center justify-center shrink-0', uploaded ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-600' : 'bg-gray-100 dark:bg-[#1A1A35] text-gray-500')}>
                      {uploaded ? <CheckCircle size={18}/> : <FileText size={18}/>}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-0.5">
                        <p className="font-medium text-sm text-gray-900 dark:text-white">{doc.label}</p>
                        {uploaded && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 text-[10px]">Uploaded</span>}
                      </div>
                      <p className="text-xs text-muted mb-2">{doc.desc}</p>
                      {!uploaded && (
                        <FileUpload category={doc.key} compact accept=".jpg,.jpeg,.png" label="Click or drag your photo here" onUploaded={url => attachDoc(doc.key, url)}/>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          <div className="mt-6 flex items-center justify-between">
            <p className="text-sm text-muted">{uploadedCategories.length} of {DOC_CATEGORIES.length} documents uploaded</p>
            <div className="flex items-center gap-2">
              <Button variant="ghost" onClick={() => setConfirmCancel(true)} leftIcon={<X size={14}/>}>Cancel</Button>
              <Button onClick={submit} loading={submitting} disabled={!allUploaded}>Submit for verification <ChevronRight size={14}/></Button>
            </div>
          </div>
        </Card>
      )}

      <ConfirmModal open={confirmCancel} onClose={() => setConfirmCancel(false)}
        onConfirm={() => { setConfirmCancel(false); router.push('/dashboard') }}
        title="Leave verification?" label="Leave" danger={false}
        message="Your uploaded documents are saved as a draft — you can come back and resume anytime."/>

      {verif?.documents && verif.documents.length > 0 && !['DRAFT'].includes(status) && (
        <Card>
          <h2 className="font-display font-semibold mb-4">Submitted documents</h2>
          <div className="space-y-3">
            {verif.documents.map(doc => (
              <div key={doc.id} className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 dark:bg-[#1A1A35]">
                <div className="w-8 h-8 rounded-lg bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  <FileText size={15}/>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{doc.documentCategory?.replace(/_/g,' ') || 'Document'}</p>
                  <p className="text-xs text-muted">Score: {doc.aiAuthenticityScore ?? 'N/A'}/100 · {fmt.date(doc.uploadedAt)}</p>
                </div>
                {doc.aiTamperDetected && <span className="badge bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400 text-xs">Tamper detected</span>}
                {doc.humanVerified && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 text-xs">Human verified</span>}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
