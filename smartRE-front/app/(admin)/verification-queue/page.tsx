'use client'
import { useEffect, useState } from 'react'
import Image from 'next/image'
import { ListChecks, CheckCircle, XCircle, FileText, AlertTriangle, Landmark, ShieldCheck } from 'lucide-react'
import { verifApi } from '@/lib/api'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import Textarea from '@/components/ui/Textarea'
import Select from '@/components/ui/Select'
import { StatusBadge } from '@/components/ui/Badge'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

export default function VerifQueuePage() {
  const [tab, setTab] = useState<'identity' | 'ownership'>('identity')
  const [idQueue, setIdQueue] = useState<any[]>([])
  const [ownQueue, setOwnQueue] = useState<any[]>([])
  const [loading, setLoad] = useState(true)
  const [modal, setModal] = useState<any | null>(null)
  const [reviewing, setRev] = useState(false)
  const [form, setForm] = useState({ decision: 'APPROVED', notes: '', resubmissionNotes: '' })
  const [ownForm, setOwnForm] = useState({
    ministryConfirmed: true, encumbranceClear: true,
    lcAdvocateStampPresent: true, lcAdvocateSignaturePresent: true, lcOfficialSealPresent: true,
    lcOwnerSignaturePresent: true, lcParcelNumberMatches: true, humanLegalApproved: true,
    decision: 'APPROVED', notes: '',
  })

  const load = async () => {
    setLoad(true)
    try {
      const [iq, oq] = await Promise.all([verifApi.idAdminQueue(), verifApi.ownerAdminQueue()])
      setIdQueue(iq.content || [])
      setOwnQueue(Array.isArray(oq) ? oq : oq.content || [])
    } finally { setLoad(false) }
  }
  useEffect(() => { load() }, [])

  const reviewIdentity = async () => {
    if (!modal) return
    setRev(true)
    try {
      await verifApi.idAdminReview(modal.id, form)
      toast.success(`Verification ${form.decision}`)
      setModal(null); load()
    } catch (e: any) { toast.error(e.response?.data?.error || 'Review failed') }
    finally { setRev(false) }
  }

  const advanceOwnership = async () => {
    if (!modal) return
    setRev(true)
    try {
      if (modal.status === 'MINISTRY_LANDS_CHECK') {
        await verifApi.ownerAdminMinistry(modal.id, ownForm.ministryConfirmed, ownForm.notes)
      } else if (modal.status === 'ENCUMBRANCE_CHECK') {
        await verifApi.ownerAdminEncumb(modal.id, ownForm.encumbranceClear, ownForm.notes)
      } else if (modal.status === 'LEGAL_REVIEW') {
        await verifApi.ownerAdminLegal(modal.id, {
          lcAdvocateStampPresent: ownForm.lcAdvocateStampPresent,
          lcAdvocateSignaturePresent: ownForm.lcAdvocateSignaturePresent,
          lcOfficialSealPresent: ownForm.lcOfficialSealPresent,
          lcOwnerSignaturePresent: ownForm.lcOwnerSignaturePresent,
          lcParcelNumberMatches: ownForm.lcParcelNumberMatches,
          humanLegalApproved: ownForm.humanLegalApproved,
          humanReviewNotes: ownForm.notes,
        })
      } else {
        await verifApi.ownerAdminFinal(modal.id, {
          decision: ownForm.decision, notes: ownForm.notes,
          ministryLandsConfirmed: ownForm.ministryConfirmed, encumbranceClear: ownForm.encumbranceClear,
        })
      }
      toast.success('Ownership verification updated')
      setModal(null); load()
    } catch (e: any) { toast.error(e.response?.data?.error || 'Update failed') }
    finally { setRev(false) }
  }

  if (loading) return <PageLoader/>

  const queue = tab === 'identity' ? idQueue : ownQueue

  return (
    <div className="space-y-5">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Verification Queue</h1>
        <p className="text-muted text-[13px] mt-1">{queue.length} pending review{queue.length !== 1 ? 's' : ''}</p>
      </div>

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
          {queue.map((item: any) => (
            <Card key={item.id}>
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  {tab === 'identity' ? <FileText size={17}/> : <Landmark size={17}/>}
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 dark:text-white text-[13px]">
                      {tab === 'identity' ? `User ID: ${item.userId?.slice(0, 8)}...` : `Property ID: ${item.propertyId?.slice(0, 8)}...`}
                    </p>
                    <StatusBadge status={item.status} size="sm"/>
                  </div>
                  {tab === 'identity' ? (
                    <p className="text-[12px] text-muted">Score: {item.identityScore}/100 · Docs: {item.documents?.length || 0} uploaded · {fmt.date(item.createdAt)}</p>
                  ) : (
                    <p className="text-[12px] text-muted">{item.county} · {item.propertyType} · Docs: {item.documents?.length || 0} uploaded · {fmt.date(item.createdAt)}</p>
                  )}
                  {item.fraudStrikeCount > 0 && <p className="text-[12px] text-amber-600 mt-1 flex items-center gap-1"><AlertTriangle size={11}/>Fraud strikes: {item.fraudStrikeCount}</p>}
                </div>
                <Button size="sm" onClick={() => { setModal({ ...item, _tab: tab }); setForm({ decision: 'APPROVED', notes: '', resubmissionNotes: '' }) }}>Review</Button>
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
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>User: {modal.userId}</p>
              <p>AI Score: <strong>{modal.identityScore}/100</strong></p>
            </div>
            {modal.documents && modal.documents.length > 0 && (
              <div>
                <p className="text-[12px] font-medium text-gray-600 dark:text-gray-400 mb-2">Uploaded documents ({modal.documents.length})</p>
                <div className="grid grid-cols-2 gap-2">
                  {modal.documents.map((d: any) => (
                    <a key={d.id} href={d.documentUrl} target="_blank" rel="noopener noreferrer"
                      className="group relative aspect-video rounded-lg overflow-hidden border border-base bg-gray-100 dark:bg-[#1A1509]">
                      <Image src={d.documentUrl} alt={d.documentCategory} fill sizes="200px" className="object-cover"/>
                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/50 transition-colors flex items-center justify-center">
                        <span className="opacity-0 group-hover:opacity-100 text-white text-[11px] font-medium transition-opacity">View full size</span>
                      </div>
                      <span className="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[10px] px-2 py-1 truncate">{d.documentCategory?.replace(/_/g, ' ')}</span>
                    </a>
                  ))}
                </div>
              </div>
            )}
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
            {modal?.status === 'HUMAN_REVIEW' ? 'Submit final decision' : 'Advance to next stage'}
          </Button></>}>
        {modal && modal._tab === 'ownership' && (
          <div className="space-y-4">
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>County: {modal.county} · Parcel: {modal.parcelNumber || 'N/A'}</p>
              <p>Title deed: {modal.titleDeedNumber || 'N/A'} · LR: {modal.lrNumber || 'N/A'}</p>
            </div>
            {modal.documents && modal.documents.length > 0 && (
              <div>
                <p className="text-[12px] font-medium text-gray-600 dark:text-gray-400 mb-2">Uploaded documents ({modal.documents.length})</p>
                <div className="grid grid-cols-2 gap-2">
                  {modal.documents.map((d: any) => (
                    <a key={d.id} href={d.documentUrl} target="_blank" rel="noopener noreferrer"
                      className="group relative aspect-video rounded-lg overflow-hidden border border-base bg-gray-100 dark:bg-[#1A1509]">
                      <Image src={d.documentUrl} alt={d.documentCategory} fill sizes="200px" className="object-cover"/>
                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/50 transition-colors flex items-center justify-center">
                        <span className="opacity-0 group-hover:opacity-100 text-white text-[11px] font-medium transition-opacity">View full size</span>
                      </div>
                      <span className="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[10px] px-2 py-1 truncate">{d.documentCategory?.replace(/_/g, ' ')}</span>
                    </a>
                  ))}
                </div>
              </div>
            )}

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
                {[
                  ['lcAdvocateStampPresent', 'Advocate stamp present'],
                  ['lcAdvocateSignaturePresent', 'Advocate signature present'],
                  ['lcOfficialSealPresent', 'Official seal present'],
                  ['lcOwnerSignaturePresent', 'Owner signature present'],
                  ['lcParcelNumberMatches', 'Parcel number matches'],
                  ['humanLegalApproved', 'Overall legal approval'],
                ].map(([key, label]) => (
                  <label key={key} className="flex items-center gap-2 text-[12px] p-2 rounded-md border border-base cursor-pointer">
                    <input type="checkbox" checked={(ownForm as any)[key]} onChange={e => setOwnForm(f => ({ ...f, [key]: e.target.checked }))}/>
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