'use client'
import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Landmark, Upload, CheckCircle, CheckCircle2, Circle, Clock, XCircle, AlertTriangle, Building2, X, type LucideIcon } from 'lucide-react'
import { propertyApi, verifApi } from '@/lib/api'
import type { PropertyResponse, OwnershipVerificationResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import BulkFileUpload from '@/components/ui/BulkFileUpload'
import { DocumentThumbnailGrid } from '@/components/ui/DocumentThumbnailGrid'
import { PageLoader, ConfirmModal } from '@/components/ui/Modal'
import { StatusBadge } from '@/components/ui/Badge'
import { COUNTY_OPTIONS } from '@/lib/counties'
import { fmt, cn } from '@/lib/utils'
import toast from 'react-hot-toast'

const NON_TERMINAL = ['SUBMITTED', 'AI_SCREENING', 'MINISTRY_LANDS_CHECK', 'ENCUMBRANCE_CHECK', 'LEGAL_REVIEW', 'HUMAN_REVIEW']

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

const DOC_CATEGORY_LABELS: Record<string, string> = Object.fromEntries(DOC_CATEGORIES.map(c => [c.value, c.label]))

const TENURE_BY_LISTING_TYPE: Record<string, { default: string; plausible: string[] }> = {
  HOUSE:      { default: 'FREEHOLD', plausible: ['FREEHOLD', 'LEASEHOLD'] },
  TOWNHOUSE:  { default: 'FREEHOLD', plausible: ['FREEHOLD', 'LEASEHOLD'] },
  VILLA:      { default: 'FREEHOLD', plausible: ['FREEHOLD', 'LEASEHOLD'] },
  APARTMENT:  { default: 'SECTIONAL_TITLE', plausible: ['SECTIONAL_TITLE'] },
  STUDIO:     { default: 'SECTIONAL_TITLE', plausible: ['SECTIONAL_TITLE'] },
  LAND:       { default: 'FREEHOLD', plausible: ['FREEHOLD', 'LEASEHOLD', 'AGRICULTURAL'] },
  COMMERCIAL: { default: 'COMMERCIAL', plausible: ['COMMERCIAL', 'FREEHOLD', 'LEASEHOLD'] },
}

const STATUS_INFO: Record<string, { icon: LucideIcon; color: string; title: string; desc: string }> = {
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
  const router = useRouter()
  const [properties, setProperties] = useState<PropertyResponse[]>([])
  const [verifications, setVerifications] = useState<OwnershipVerificationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedPropertyId, setSelectedPropertyId] = useState('')
  const [starting, setStarting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [startForm, setStartForm] = useState({ propertyType: 'FREEHOLD', county: '', parcelNumber: '', titleDeedNumber: '', lrNumber: '' })
  const [activeVerification, setActiveVerification] = useState<OwnershipVerificationResponse | null>(null)
  const [confirmCancelStart, setConfirmCancelStart] = useState(false)
  const [confirmCancelUpload, setConfirmCancelUpload] = useState<string | null>(null)
  const [confirmRemoveDoc, setConfirmRemoveDoc] = useState<{ verificationId: string; documentId: string } | null>(null)
  const prevStatuses = useRef<Record<string, string>>({})

  const load = () => {
    setLoading(true)
    Promise.allSettled([propertyApi.my(), verifApi.myOwner()]).then(([p, v]) => {
      if (p.status === 'fulfilled') setProperties(p.value.content || [])
      if (v.status === 'fulfilled') setVerifications(v.value)
    }).finally(() => setLoading(false))
  }
  useEffect(() => { load() }, [])

  useEffect(() => {
    if (!verifications.some(v => NON_TERMINAL.includes(v.status))) return
    const interval = setInterval(() => {
      verifApi.myOwner().then(setVerifications).catch(() => {})
    }, 4000)
    return () => clearInterval(interval)
  }, [verifications])

  useEffect(() => {
    for (const v of verifications) {
      const prev = prevStatuses.current[v.id]
      prevStatuses.current[v.id] = v.status
      if (!prev || prev === v.status || !NON_TERMINAL.includes(prev)) continue
      const property = properties.find(p => p.id === v.propertyId)
      const name = property?.title || 'Your listing'
      if (v.status === 'APPROVED') toast.success(`${name}: ownership verification approved!`)
      else if (v.status === 'REJECTED') toast.error(`${name}: ownership verification rejected — see reason below.`)
    }
  }, [verifications, properties])

  const propertiesWithoutVerification = properties.filter(p => !verifications.some(v => v.propertyId === p.id))
  const selectedProperty = properties.find(p => p.id === selectedPropertyId)
  const tenureHint = selectedProperty ? TENURE_BY_LISTING_TYPE[selectedProperty.propertyType] : undefined
  const tenureMismatch = tenureHint && !tenureHint.plausible.includes(startForm.propertyType)

  const selectProperty = (propertyId: string) => {
    setSelectedPropertyId(propertyId)
    const property = properties.find(p => p.id === propertyId)
    const hint = property ? TENURE_BY_LISTING_TYPE[property.propertyType] : undefined
    if (hint) setStartForm(f => ({ ...f, propertyType: hint.default }))
  }

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

  const attachDoc = async (verificationId: string, category: string, url: string) => {
    const v = await verifApi.uploadOwnerDoc(verificationId, { documentCategory: category, documentUrl: url })
    setVerifications(list => list.map(x => x.id === verificationId ? v : x))
    setActiveVerification(v)
  }

  const removeDoc = async (verificationId: string, documentId: string) => {
    try {
      const v = await verifApi.deleteOwnerDoc(verificationId, documentId)
      setVerifications(list => list.map(x => x.id === verificationId ? v : x))
      setActiveVerification(v)
      toast.success('Document removed')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to remove document') }
    finally { setConfirmRemoveDoc(null) }
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
            } value={selectedPropertyId} onChange={e => selectProperty(e.target.value)}/>
            {selectedPropertyId && (
              <>
                <Select label="Title type" required options={PROPERTY_TYPES} value={startForm.propertyType} onChange={e => setStartForm(f => ({ ...f, propertyType: e.target.value }))}/>
                {tenureMismatch && (
                  <p className="text-[12px] text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                    <AlertTriangle size={12}/>
                    {selectedProperty?.propertyType?.toLowerCase()} listings are usually {tenureHint!.plausible.map(t => t.toLowerCase().replace('_', ' ')).join(' or ')} — double-check this is right.
                  </p>
                )}
                <Select label="County" required options={COUNTY_OPTIONS} value={startForm.county} onChange={e => setStartForm(f => ({ ...f, county: e.target.value }))}/>
                <div className="grid grid-cols-3 gap-2">
                  <Input label="Parcel number" value={startForm.parcelNumber} onChange={e => setStartForm(f => ({ ...f, parcelNumber: e.target.value }))}/>
                  <Input label="Title deed number" value={startForm.titleDeedNumber} onChange={e => setStartForm(f => ({ ...f, titleDeedNumber: e.target.value }))}/>
                  <Input label="LR number" value={startForm.lrNumber} onChange={e => setStartForm(f => ({ ...f, lrNumber: e.target.value }))}/>
                </div>
                <div className="flex items-center gap-2">
                  <Button variant="ghost" onClick={() => setConfirmCancelStart(true)} leftIcon={<X size={14}/>}>Cancel</Button>
                  <Button onClick={startVerification} loading={starting}>Start verification</Button>
                </div>
              </>
            )}
          </div>
        </Card>
      )}

      <ConfirmModal open={confirmCancelStart} onClose={() => setConfirmCancelStart(false)}
        onConfirm={() => { setConfirmCancelStart(false); setSelectedPropertyId('') }}
        title="Cancel this verification form?" label="Discard" danger={false}
        message="Nothing has been submitted yet — this will just clear the form."/>

      {verifications.map(v => {
        const info = STATUS_INFO[v.status] || STATUS_INFO.DRAFT
        const property = properties.find(p => p.id === v.propertyId)
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
                {(v.allRequiredDocuments || []).length > 0 && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                    {v.allRequiredDocuments!.map(r => (
                      <div key={r.documentCategory} className="flex items-center gap-1.5 text-[11px]">
                        {r.uploaded
                          ? <CheckCircle2 size={12} className="text-emerald-500 shrink-0"/>
                          : <Circle size={12} className="text-gray-300 dark:text-gray-600 shrink-0"/>}
                        <span className={cn('truncate', r.uploaded ? 'text-gray-700 dark:text-gray-300' : 'text-muted')}>
                          {DOC_CATEGORY_LABELS[r.documentCategory] || r.documentCategory.replace(/_/g, ' ')}
                        </span>
                        {!r.isMandatory && <span className="text-[9px] text-muted shrink-0">(optional)</span>}
                      </div>
                    ))}
                  </div>
                )}
                <BulkFileUpload
                  requiredCategories={v.allRequiredDocuments || []}
                  categoryLabels={DOC_CATEGORY_LABELS}
                  onFileRegistered={(category, url) => attachDoc(v.id, category, url)}
                />
                {(v.documents || []).length > 0 && (
                  <DocumentThumbnailGrid
                    title="Review before submitting — remove and re-upload if a document is mis-tagged"
                    documents={v.documents.map(d => ({ id: d.id, url: d.documentUrl, label: d.documentCategory }))}
                    onRemove={doc => setConfirmRemoveDoc({ verificationId: v.id, documentId: doc.id })}
                  />
                )}
                <div className="flex items-center gap-2">
                  <Button variant="ghost" onClick={() => setConfirmCancelUpload(v.id)} leftIcon={<X size={13}/>}>Cancel</Button>
                  <Button onClick={() => submitVerification(v.id)} loading={submitting} disabled={(v.missingDocuments?.length ?? 0) > 0} leftIcon={<Upload size={13}/>}>
                    Submit for review
                  </Button>
                </div>
              </div>
            )}
          </Card>
        )
      })}

      <ConfirmModal open={!!confirmRemoveDoc} onClose={() => setConfirmRemoveDoc(null)}
        onConfirm={() => confirmRemoveDoc && removeDoc(confirmRemoveDoc.verificationId, confirmRemoveDoc.documentId)}
        title="Remove this document?" label="Remove" danger
        message="You'll need to re-upload a replacement before you can submit."/>

      <ConfirmModal open={!!confirmCancelUpload} onClose={() => setConfirmCancelUpload(null)}
        onConfirm={() => { setConfirmCancelUpload(null); router.push('/listings') }}
        title="Leave verification?" label="Leave" danger={false}
        message="Your uploaded documents are saved as a draft — you can come back and resume anytime."/>

      {verifications.length === 0 && propertiesWithoutVerification.length === 0 && properties.length === 0 && (
        <Card>
          <p className="text-[13px] text-muted text-center py-6 flex items-center justify-center gap-2"><AlertTriangle size={14}/>List a property first, then start its ownership verification here.</p>
        </Card>
      )}
    </div>
  )
}
