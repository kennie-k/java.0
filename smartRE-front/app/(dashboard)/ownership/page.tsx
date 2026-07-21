'use client'
import { useEffect, useState } from 'react'
import { Landmark, Upload, CheckCircle, Clock, XCircle, AlertTriangle, FileText, Building2 } from 'lucide-react'
import { propertyApi, verifApi } from '@/lib/api'
import type { PropertyResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import FileUpload from '@/components/ui/FileUpload'
import { PageLoader } from '@/components/ui/Modal'
import { StatusBadge } from '@/components/ui/Badge'
import { fmt, cn } from '@/lib/utils'
import toast from 'react-hot-toast'

const DOC_CATEGORIES = [
  { value: 'TITLE_DEED', label: 'Title deed' },
  { value: 'LAND_SEARCH_CERTIFICATE', label: 'Land search certificate' },
  { value: 'LAND_RENT_CLEARANCE', label: 'Land rent clearance' },
  { value: 'RATES_CLEARANCE_CERTIFICATE', label: 'Rates clearance certificate' },
  { value: 'CONSENT_TO_TRANSFER', label: 'Consent to transfer' },
  { value: 'LAND_CONTROL_BOARD_CONSENT', label: 'Land Control Board consent' },
  { value: 'TRANSFER_FORM_RL1', label: 'Transfer form (RL1)' },
  { value: 'SURVEY_MAP', label: 'Survey map' },
  { value: 'SPOUSAL_CONSENT', label: 'Spousal consent' },
  { value: 'MUTATION_FORM', label: 'Mutation form' },
  { value: 'LEASE_AGREEMENT', label: 'Lease agreement' },
  { value: 'LANDLORD_AUTHORIZATION', label: 'Landlord authorization' },
  { value: 'PROBATE_GRANT', label: 'Probate grant' },
  { value: 'POWER_OF_ATTORNEY', label: 'Power of attorney' },
  { value: 'SERVICE_CHARGE_CLEARANCE', label: 'Service charge clearance' },
  { value: 'BUSINESS_PERMIT', label: 'Business permit' },
]

const PROPERTY_TYPES = [
  { value: 'FREEHOLD', label: 'Freehold' },
  { value: 'LEASEHOLD', label: 'Leasehold' },
  { value: 'SECTIONAL_TITLE', label: 'Sectional title' },
  { value: 'AGRICULTURAL', label: 'Agricultural' },
  { value: 'COMMERCIAL', label: 'Commercial' },
]

const STATUS_INFO: Record<string, { icon: any; color: string; title: string; desc: string }> = {
  DRAFT: { icon: Clock, color: 'text-gray-400', title: 'Not started', desc: 'Provide title details and upload documents to begin.' },
  SUBMITTED: { icon: Clock, color: 'text-amber-500', title: 'Submitted', desc: 'Document hashes are being checked and AI screening is starting.' },
  AI_SCREENING: { icon: Clock, color: 'text-amber-500', title: 'AI screening', desc: 'Documents are being scored; Ardhisasa check runs at the end of this step.' },
  MINISTRY_LANDS_CHECK: { icon: Clock, color: 'text-amber-500', title: 'Ministry of Lands check', desc: 'An admin is verifying your title against the Ardhisasa registry.' },
  ENCUMBRANCE_CHECK: { icon: Clock, color: 'text-amber-500', title: 'Encumbrance check', desc: 'An admin is confirming there are no caveats, court orders, or bank charges.' },
  LEGAL_REVIEW: { icon: Clock, color: 'text-amber-500', title: 'Legal review', desc: 'An admin is verifying the advocate stamp, signatures, and official seal.' },
  HUMAN_REVIEW: { icon: Clock, color: 'text-amber-500', title: 'Final review', desc: 'All checks passed; an admin is making the final approval decision.' },
  APPROVED: { icon: CheckCircle, color: 'text-emerald-500', title: 'Verified', desc: 'Ownership confirmed. Your listing is now active.' },
  REJECTED: { icon: XCircle, color: 'text-red-500', title: 'Rejected', desc: 'See the rejection reason below. You can start a new submission.' },
}

export default function OwnershipPage() {
  const [properties, setProperties] = useState<PropertyResponse[]>([])
  const [verifications, setVerifications] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedPropertyId, setSelectedPropertyId] = useState('')
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [startForm, setStartForm] = useState({ propertyType: 'FREEHOLD', county: '', parcelNumber: '', titleDeedNumber: '', lrNumber: '' })
  const [activeVerification, setActiveVerification] = useState<any | null>(null)
  const [docCategory, setDocCategory] = useState('TITLE_DEED')

  const load = () => {
    setLoading(true)
    Promise.allSettled([propertyApi.my(), verifApi.myOwner()]).then(([p, v]) => {
      if (p.status === 'fulfilled') setProperties(p.value.content || [])
      if (v.status === 'fulfilled') setVerifications(Array.isArray(v.value) ? v.value : [])
    }).finally(() => setLoading(false))
  }
  useEffect(() => { load() }, [])

  const propertiesWithoutVerification = properties.filter(p => !verifications.some(v => v.propertyId === p.id))

  const startVerification = async () => {
    if (!selectedPropertyId) { toast.error('Select a property'); return }
    if (!startForm.county.trim()) { toast.error('County is required'); return }
    setStarting(true)
    try {
      const v = await verifApi.startOwner({ propertyId: selectedPropertyId, ...startForm })
      toast.success('Ownership verification started')
      setVerifications(list => [...list, v])
      setActiveVerification(v)
      setSelectedPropertyId('')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to start verification') }
    finally { setStarting(false) }
  }

  const attachDoc = async (verificationId: string, url: string) => {
    try {
      const v = await verifApi.uploadOwnerDoc(verificationId, { documentCategory: docCategory, documentUrl: url })
      setVerifications(list => list.map(x => x.id === verificationId ? v : x))
      setActiveVerification(v)
      toast.success('Document attached')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to attach document') }
  }

  const submitVerification = async (verificationId: string) => {
    setSubmitting(true)
    try {
      const v = await verifApi.submitOwner(verificationId)
      setVerifications(list => list.map(x => x.id === verificationId ? v : x))
      setActiveVerification(v)
      toast.success('Submitted for review')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Submission failed') }
    finally { setSubmitting(false) }
  }

  if (loading) return <PageLoader/>

  return (
    <div className="max-w-3xl mx-auto space-y-5">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Land title verification</h1>
        <p className="text-muted text-[13px] mt-1">Required for each listing before it can go active</p>
      </div>

      {propertiesWithoutVerification.length > 0 && (
        <Card>
          <h2 className="font-display font-semibold text-[14px] mb-3 flex items-center gap-1.5"><Building2 size={15} className="text-gold-500"/>Start a new verification</h2>
          <div className="space-y-3">
            <Select label="Property" required options={
              propertiesWithoutVerification.map(p => ({ value: p.id, label: p.title }))
            } value={selectedPropertyId} onChange={e => setSelectedPropertyId(e.target.value)}/>
            {selectedPropertyId && (
              <>
                <Select label="Title type" required options={PROPERTY_TYPES} value={startForm.propertyType} onChange={e => setStartForm(f => ({ ...f, propertyType: e.target.value }))}/>
                <Input label="County" required value={startForm.county} onChange={e => setStartForm(f => ({ ...f, county: e.target.value }))}/>
                <div className="grid grid-cols-3 gap-2">
                  <Input label="Parcel number" value={startForm.parcelNumber} onChange={e => setStartForm(f => ({ ...f, parcelNumber: e.target.value }))}/>
                  <Input label="Title deed number" value={startForm.titleDeedNumber} onChange={e => setStartForm(f => ({ ...f, titleDeedNumber: e.target.value }))}/>
                  <Input label="LR number" value={startForm.lrNumber} onChange={e => setStartForm(f => ({ ...f, lrNumber: e.target.value }))}/>
                </div>
                <Button onClick={startVerification} loading={starting}>Start verification</Button>
              </>
            )}
          </div>
        </Card>
      )}

      {verifications.map(v => {
        const info = STATUS_INFO[v.status] || STATUS_INFO.DRAFT
        const property = properties.find(p => p.id === v.propertyId)
        const uploadedCategories = (v.documents || []).map((d: any) => d.documentCategory)
        const canUpload = ['DRAFT', 'REJECTED'].includes(v.status)
        return (
          <Card key={v.id}>
            <div className="flex items-center gap-4">
              <div className={cn('w-12 h-12 rounded-2xl flex items-center justify-center shrink-0', v.status === 'APPROVED' ? 'bg-emerald-100 dark:bg-emerald-500/15' : v.status === 'REJECTED' ? 'bg-red-50 dark:bg-red-500/10' : 'bg-gold-50 dark:bg-gold-500/10')}>
                <info.icon size={22} className={info.color}/>
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-0.5">
                  <h3 className="font-display font-semibold text-[14px] text-gray-900 dark:text-white">{property?.title || 'Listing'}</h3>
                  <StatusBadge status={v.status} size="sm"/>
                </div>
                <p className="text-[12px] text-muted">{info.desc}</p>
              </div>
            </div>

            {v.rejectionReason && (
              <div className="mt-3 p-3 bg-red-50 dark:bg-red-500/10 rounded-lg">
                <p className="text-[12px] text-red-600 dark:text-red-400"><strong>Rejection reason:</strong> {v.rejectionReason}</p>
              </div>
            )}

            {canUpload && (
              <div className="mt-4 pt-4 border-t border-base space-y-3">
                <div className="flex items-center gap-2">
                  <select value={docCategory} onChange={e => setDocCategory(e.target.value)} className="input-base flex-1">
                    {DOC_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                </div>
                <FileUpload category={docCategory} compact accept=".jpg,.jpeg,.png,.pdf" label="Upload this document"
                  onUploaded={url => attachDoc(v.id, url)}/>
                {(v.documents || []).length > 0 && (
                  <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                    {v.documents.map((d: any) => (
                      <div key={d.id} className="flex items-center gap-1.5 bg-gray-50 dark:bg-[#2E2518] rounded-md px-2 py-1.5 text-[11px]">
                        <FileText size={11} className="text-gold-500 shrink-0"/>
                        <span className="truncate">{d.documentCategory?.replace(/_/g, ' ')}</span>
                      </div>
                    ))}
                  </div>
                )}
                <Button onClick={() => submitVerification(v.id)} loading={submitting} disabled={uploadedCategories.length === 0} leftIcon={<Upload size={13}/>}>
                  Submit for review
                </Button>
              </div>
            )}
          </Card>
        )
      })}

      {verifications.length === 0 && propertiesWithoutVerification.length === 0 && properties.length === 0 && (
        <Card>
          <p className="text-[13px] text-muted text-center py-6 flex items-center justify-center gap-2"><AlertTriangle size={14}/>List a property first, then start its ownership verification here.</p>
        </Card>
      )}
    </div>
  )
}
