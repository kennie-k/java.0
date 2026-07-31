'use client'
import { Suspense, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { ListChecks, CheckCircle, XCircle, FileText, AlertTriangle, Landmark, ShieldCheck, ChevronLeft, ChevronRight } from 'lucide-react'
import { verifApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticRemoveFromPage, rollbackPage } from '@/lib/queryClientHelpers'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import { DocumentThumbnailGrid } from '@/components/ui/DocumentThumbnailGrid'
import Textarea from '@/components/ui/Textarea'
import Select from '@/components/ui/Select'
import { StatusBadge } from '@/components/ui/Badge'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'
import type { IdentityVerificationResponse, OwnershipVerificationResponse } from '@/types'

const ID_KEY = queryKeys.identityAdminQueue('HUMAN_REVIEW')

const OWN_STATUSES = ['MINISTRY_LANDS_CHECK', 'ENCUMBRANCE_CHECK', 'LEGAL_REVIEW', 'HUMAN_REVIEW'] as const
const OWN_KEY_PREFIX = ['admin', 'verification', 'ownership'] as const

type ModalItem =
  | (IdentityVerificationResponse & { _tab: 'identity' })
  | (OwnershipVerificationResponse & { _tab: 'ownership' })

export default function VerifQueuePage() {
  return <Suspense fallback={<PageLoader/>}><VerifQueuePageInner/></Suspense>
}

function VerifQueuePageInner() {
  const qc = useQueryClient()
  const sp = useSearchParams()
  const tabParam = sp.get('tab')
  const [tab, setTab] = useState<'identity' | 'ownership'>(tabParam === 'ownership' ? 'ownership' : 'identity')
  useEffect(() => {
    if (tabParam === 'identity' || tabParam === 'ownership') setTab(tabParam)
  }, [tabParam])
  const [modal, setModal] = useState<ModalItem | null>(null)
  const [form, setForm] = useState({ decision: 'APPROVED', notes: '', resubmissionNotes: '' })
  const defaultOwnForm = () => ({
    ministryConfirmed: true, encumbranceClear: true,
    lcAdvocateStampPresent: true, lcAdvocateSignaturePresent: true, lcOfficialSealPresent: true,
    lcOwnerSignaturePresent: true, lcParcelNumberMatches: true, humanLegalApproved: true,
    decision: 'APPROVED', notes: '',
  })
  const [ownForm, setOwnForm] = useState(defaultOwnForm())

  const idQueueQuery = useQuery({ queryKey: ID_KEY, queryFn: () => verifApi.idAdminQueue() })
  const ownQueueQueries = useQueries({
    queries: OWN_STATUSES.map(status => ({
      queryKey: queryKeys.ownershipAdminQueue(status),
      queryFn: () => verifApi.ownerAdminQueue(status),
    })),
  })

  const idQueue = idQueueQuery.data?.content ?? []
  const ownQueue = useMemo(
    () => ownQueueQueries
      .flatMap(q => q.data?.content ?? [])
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()),
    [ownQueueQueries]
  )
  const loading = idQueueQuery.isLoading || ownQueueQueries.some(q => q.isLoading)
  const loadError = idQueueQuery.isError || ownQueueQueries.some(q => q.isError)

  const openIdentity = (item: IdentityVerificationResponse) => {
    setModal({ ...item, _tab: 'identity' })
    setForm({ decision: 'APPROVED', notes: '', resubmissionNotes: '' })
  }
  const openOwnership = (item: OwnershipVerificationResponse) => {
    setModal({ ...item, _tab: 'ownership' })
    setOwnForm(defaultOwnForm())
  }

  const idModalIndex = modal?._tab === 'identity' ? idQueue.findIndex(x => x.id === modal.id) : -1
  const ownModalIndex = modal?._tab === 'ownership' ? ownQueue.findIndex(x => x.id === modal.id) : -1

  const reviewIdentityMutation = useMutation({
    mutationFn: (vars: { id: string; form: typeof form }) => verifApi.idAdminReview(vars.id, vars.form),
    onMutate: vars => optimisticRemoveFromPage<IdentityVerificationResponse>(qc, ID_KEY, vars.id),
    onError: (_e, _v, previous) => rollbackPage(qc, ID_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: ID_KEY }),
  })

  const advanceOwnershipMutation = useMutation({
    mutationFn: (item: OwnershipVerificationResponse) => {
      if (item.status === 'MINISTRY_LANDS_CHECK') {
        return verifApi.ownerAdminMinistry(item.id, ownForm.ministryConfirmed, ownForm.notes)
      }
      if (item.status === 'ENCUMBRANCE_CHECK') {
        return verifApi.ownerAdminEncumb(item.id, ownForm.encumbranceClear, ownForm.notes)
      }
      if (item.status === 'LEGAL_REVIEW') {
        return verifApi.ownerAdminLegal(item.id, {
          lcAdvocateStampPresent: ownForm.lcAdvocateStampPresent,
          lcAdvocateSignaturePresent: ownForm.lcAdvocateSignaturePresent,
          lcOfficialSealPresent: ownForm.lcOfficialSealPresent,
          lcOwnerSignaturePresent: ownForm.lcOwnerSignaturePresent,
          lcParcelNumberMatches: ownForm.lcParcelNumberMatches,
          humanLegalApproved: ownForm.humanLegalApproved,
          humanReviewNotes: ownForm.notes,
        })
      }
      return verifApi.ownerAdminFinal(item.id, {
        decision: ownForm.decision, notes: ownForm.notes,
        ministryLandsConfirmed: ownForm.ministryConfirmed, encumbranceClear: ownForm.encumbranceClear,
      })
    },
    onMutate: item => optimisticRemoveFromPage<OwnershipVerificationResponse>(qc, queryKeys.ownershipAdminQueue(item.status), item.id),
    onError: (_e, item, previous) => rollbackPage(qc, queryKeys.ownershipAdminQueue(item.status), previous),
    onSettled: () => qc.invalidateQueries({ queryKey: OWN_KEY_PREFIX }),
  })

  const reviewing = reviewIdentityMutation.isPending || advanceOwnershipMutation.isPending

  const reviewIdentity = async () => {
    if (!modal || modal._tab !== 'identity') return
    try {
      await reviewIdentityMutation.mutateAsync({ id: modal.id, form })
      toast.success(`Verification ${form.decision}`)
      setModal(null)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Review failed') }
  }

  const advanceOwnership = async () => {
    if (!modal || modal._tab !== 'ownership') return
    try {
      await advanceOwnershipMutation.mutateAsync(modal)
      toast.success('Ownership verification updated')
      setModal(null)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Update failed') }
  }

  if (loading) return <PageLoader/>

  const queue = tab === 'identity' ? idQueue : ownQueue

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Verification Queue</h1>
        <p className="text-muted text-[13px] mt-1">{queue.length} pending review{queue.length !== 1 ? 's' : ''}</p>
      </div>

      {loadError && <InlineError message="Failed to load one or more verification queues."/>}

      <div className="flex bg-gray-100 dark:bg-[#2E2518] rounded-lg p-1 w-fit">
        <button onClick={() => setTab('identity')} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[13px] font-medium transition-colors ${tab === 'identity' ? 'bg-white dark:bg-[#201911] text-gray-900 dark:text-white shadow-sm' : 'text-muted'}`}>
          <ShieldCheck size={13}/>Identity ({idQueue.length})
        </button>
        <button onClick={() => setTab('ownership')} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[13px] font-medium transition-colors ${tab === 'ownership' ? 'bg-white dark:bg-[#201911] text-gray-900 dark:text-white shadow-sm' : 'text-muted'}`}>
          <Landmark size={13}/>Ownership ({ownQueue.length})
        </button>
      </div>

      {queue.length === 0 ? (
        <EmptyState icon={<ListChecks size={28}/>} title="Queue is clear" desc={`No ${tab} verifications pending admin review.`}/>
      ) : (
        <div className="space-y-3">
          {tab === 'identity' ? idQueue.map(item => (
            <Card key={item.id}>
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  <FileText size={17}/>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 dark:text-white text-[13px]">User ID: {item.userId?.slice(0, 8)}...</p>
                    <StatusBadge status={item.status} size="sm"/>
                  </div>
                  <p className="text-[12px] text-muted">Score: {item.identityScore}/100 · Docs: {item.documents?.length || 0} uploaded · {fmt.date(item.createdAt)}</p>
                  {item.fraudStrikeCount > 0 && <p className="text-[12px] text-amber-600 mt-1 flex items-center gap-1"><AlertTriangle size={11}/>Fraud strikes: {item.fraudStrikeCount}</p>}
                </div>
                <Button size="sm" onClick={() => openIdentity(item)}>Review</Button>
              </div>
            </Card>
          )) : ownQueue.map(item => (
            <Card key={item.id}>
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  <Landmark size={17}/>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 dark:text-white text-[13px]">Property ID: {item.propertyId?.slice(0, 8)}...</p>
                    <StatusBadge status={item.status} size="sm"/>
                  </div>
                  <p className="text-[12px] text-muted">{item.county} · {item.propertyType} · Docs: {item.documents?.length || 0} uploaded · {fmt.date(item.createdAt)}</p>
                </div>
                <Button size="sm" onClick={() => openOwnership(item)}>Review</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={!!modal && modal._tab === 'identity'} onClose={() => setModal(null)} title="Review identity verification"
        footer={<><Button variant="secondary" onClick={() => setModal(null)}>Cancel</Button>
          <Button variant={form.decision === 'APPROVED' ? 'primary' : 'danger'} onClick={reviewIdentity} loading={reviewing}
            leftIcon={form.decision === 'APPROVED' ? <CheckCircle size={13}/> : <XCircle size={13}/>}>
            {form.decision === 'APPROVED' ? 'Approve' : 'Reject'}
          </Button></>}>
        {modal && modal._tab === 'identity' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between text-[12px] text-muted">
              <Button size="sm" variant="ghost" leftIcon={<ChevronLeft size={14}/>} disabled={idModalIndex <= 0}
                onClick={() => openIdentity(idQueue[idModalIndex - 1])}>Previous</Button>
              <span>{idModalIndex + 1} of {idQueue.length}</span>
              <Button size="sm" variant="ghost" onClick={() => openIdentity(idQueue[idModalIndex + 1])}
                disabled={idModalIndex === -1 || idModalIndex >= idQueue.length - 1}>
                Next <ChevronRight size={14}/>
              </Button>
            </div>
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>User: {modal.userId}</p>
              <p>AI Score: <strong>{modal.identityScore}/100</strong></p>
            </div>
            <DocumentThumbnailGrid documents={modal.documents.map(d => ({ id: d.id, url: d.documentUrl, label: d.documentCategory }))}/>
            <Select label="Decision" required options={[{ value: 'APPROVED', label: 'Approve' }, { value: 'REJECTED', label: 'Reject' }]}
              value={form.decision} onChange={e => setForm(f => ({ ...f, decision: e.target.value }))}/>
            <Textarea label="Review notes" value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}/>
            {form.decision === 'REJECTED' && <Textarea label="Resubmission instructions" value={form.resubmissionNotes} onChange={e => setForm(f => ({ ...f, resubmissionNotes: e.target.value }))}/>}
          </div>
        )}
      </Modal>

      <Modal open={!!modal && modal._tab === 'ownership'} onClose={() => setModal(null)} title="Review ownership verification"
        footer={<><Button variant="secondary" onClick={() => setModal(null)}>Cancel</Button>
          <Button onClick={advanceOwnership} loading={reviewing}>
            {modal && modal._tab === 'ownership' && modal.status === 'HUMAN_REVIEW' ? 'Submit final decision' : 'Advance to next stage'}
          </Button></>}>
        {modal && modal._tab === 'ownership' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between text-[12px] text-muted">
              <Button size="sm" variant="ghost" leftIcon={<ChevronLeft size={14}/>} disabled={ownModalIndex <= 0}
                onClick={() => openOwnership(ownQueue[ownModalIndex - 1])}>Previous</Button>
              <span>{ownModalIndex + 1} of {ownQueue.length}</span>
              <Button size="sm" variant="ghost" onClick={() => openOwnership(ownQueue[ownModalIndex + 1])}
                disabled={ownModalIndex === -1 || ownModalIndex >= ownQueue.length - 1}>
                Next <ChevronRight size={14}/>
              </Button>
            </div>
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>County: {modal.county} · Parcel: {modal.parcelNumber || 'N/A'}</p>
              <p>Title deed: {modal.titleDeedNumber || 'N/A'} · LR: {modal.lrNumber || 'N/A'}</p>
            </div>
            <DocumentThumbnailGrid documents={modal.documents.map(d => ({ id: d.id, url: d.documentUrl, label: d.documentCategory }))}/>

            {modal.status === 'MINISTRY_LANDS_CHECK' && (
              <Select label="Ministry of Lands / Ardhisasa confirmed?" required
                options={[{ value: 'true', label: 'Confirmed' }, { value: 'false', label: 'Not confirmed' }]}
                value={String(ownForm.ministryConfirmed)} onChange={e => setOwnForm(f => ({ ...f, ministryConfirmed: e.target.value === 'true' }))}/>
            )}
            {modal.status === 'ENCUMBRANCE_CHECK' && (
              <Select label="Free of caveats, court orders, and bank charges?" required
                options={[{ value: 'true', label: 'Clear' }, { value: 'false', label: 'Encumbered' }]}
                value={String(ownForm.encumbranceClear)} onChange={e => setOwnForm(f => ({ ...f, encumbranceClear: e.target.value === 'true' }))}/>
            )}
            {modal.status === 'LEGAL_REVIEW' && (
              <div className="grid grid-cols-2 gap-2">
                {([
                  ['lcAdvocateStampPresent', 'Advocate stamp present'],
                  ['lcAdvocateSignaturePresent', 'Advocate signature present'],
                  ['lcOfficialSealPresent', 'Official seal present'],
                  ['lcOwnerSignaturePresent', 'Owner signature present'],
                  ['lcParcelNumberMatches', 'Parcel number matches'],
                  ['humanLegalApproved', 'Overall legal approval'],
                ] as const).map(([key, label]) => (
                  <label key={key} className="flex items-center gap-2 text-[12px] p-2 rounded-md border border-base cursor-pointer">
                    <input type="checkbox" checked={ownForm[key]} onChange={e => setOwnForm(f => ({ ...f, [key]: e.target.checked }))}/>
                    {label}
                  </label>
                ))}
              </div>
            )}
            {modal.status === 'HUMAN_REVIEW' && (
              <Select label="Final decision" required options={[{ value: 'APPROVED', label: 'Approve' }, { value: 'REJECTED', label: 'Reject' }]}
                value={ownForm.decision} onChange={e => setOwnForm(f => ({ ...f, decision: e.target.value }))}/>
            )}
            <Textarea label="Notes" value={ownForm.notes} onChange={e => setOwnForm(f => ({ ...f, notes: e.target.value }))}/>
          </div>
        )}
      </Modal>
    </div>
  )
}
