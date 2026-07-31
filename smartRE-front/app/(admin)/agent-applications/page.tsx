'use client'
import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Briefcase, CheckCircle, XCircle, ExternalLink, Search } from 'lucide-react'
import { agentApplicationApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticRemoveFromPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { AgentApplicationResponse, AgentApplicationStatus } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import Textarea from '@/components/ui/Textarea'
import Select from '@/components/ui/Select'
import { StatusBadge } from '@/components/ui/Badge'
import { DocumentThumbnailGrid } from '@/components/ui/DocumentThumbnailGrid'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const VALID_STATUSES: AgentApplicationStatus[] = ['SUBMITTED', 'APPROVED', 'REJECTED']

export default function AgentApplicationsQueuePage() {
  return <Suspense fallback={<PageLoader/>}><AgentApplicationsQueuePageInner/></Suspense>
}

function AgentApplicationsQueuePageInner() {
  const qc = useQueryClient()
  const sp = useSearchParams()
  const statusParam = sp.get('status')
  const [status, setStatus] = useState<AgentApplicationStatus>(
    statusParam && VALID_STATUSES.includes(statusParam as AgentApplicationStatus) ? statusParam as AgentApplicationStatus : 'SUBMITTED'
  )
  useEffect(() => {
    setStatus(statusParam && VALID_STATUSES.includes(statusParam as AgentApplicationStatus) ? statusParam as AgentApplicationStatus : 'SUBMITTED')
  }, [statusParam])
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState<AgentApplicationResponse | null>(null)
  const [form, setForm] = useState({ decision: 'APPROVED', notes: '' })

  const APPLICATIONS_KEY = queryKeys.agentApplications(status)
  const { data, isLoading: loading, error } = useQuery({
    queryKey: APPLICATIONS_KEY,
    queryFn: () => agentApplicationApi.adminQueue(status),
  })
  const queue = data?.content ?? []
  const filtered = queue.filter(a =>
    !search || (a.businessName || '').toLowerCase().includes(search.toLowerCase()) || a.userId.toLowerCase().includes(search.toLowerCase())
  )

  const reviewMutation = useMutation({
    mutationFn: (vars: { id: string; form: typeof form }) => agentApplicationApi.adminReview(vars.id, vars.form),
    onMutate: vars => optimisticRemoveFromPage<AgentApplicationResponse>(qc, APPLICATIONS_KEY, vars.id),
    onError: (_e, _v, previous) => rollbackPage(qc, APPLICATIONS_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: APPLICATIONS_KEY }),
  })

  const review = async () => {
    if (!modal) return
    try {
      await reviewMutation.mutateAsync({ id: modal.id, form })
      toast.success(`Application ${form.decision.toLowerCase()}`)
      setModal(null)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Review failed') }
  }

  if (loading) return <PageLoader/>

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Agent Applications</h1>
          <p className="text-muted text-[13px] mt-1">{queue.length} {status.toLowerCase()}</p>
        </div>
        <div className="w-48">
          <Select label="Status" options={[
            { value: 'SUBMITTED', label: 'Submitted' },
            { value: 'APPROVED', label: 'Approved' },
            { value: 'REJECTED', label: 'Rejected' },
          ]} value={status} onChange={e => setStatus(e.target.value as AgentApplicationStatus)}/>
        </div>
      </div>

      {error && <InlineError message="Failed to load agent applications."/>}

      {queue.length > 0 && (
        <Input leftIcon={<Search size={15}/>} placeholder="Search by business name or user ID..." value={search} onChange={e => setSearch(e.target.value)}/>
      )}

      {queue.length === 0 ? (
        <EmptyState icon={<Briefcase size={28}/>} title="Queue is clear" desc={`No ${status.toLowerCase()} agent applications.`}/>
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search size={24}/>} title="No applications match that search"/>
      ) : (
        <div className="space-y-3">
          {filtered.map(item => (
            <Card key={item.id}>
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-gold-100 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center shrink-0">
                  <Briefcase size={17}/>
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="font-semibold text-gray-900 dark:text-white text-[13px]">{item.businessName || 'Individual applicant'}</p>
                    <StatusBadge status={item.status} size="sm"/>
                  </div>
                  <p className="text-[12px] text-muted">User: {item.userId.slice(0, 8)}... · {fmt.date(item.createdAt)}</p>
                  <a href={item.businessDocUrl} target="_blank" rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-[11px] text-gold-600 dark:text-gold-400 mt-1.5 hover:underline">
                    View business document <ExternalLink size={10}/>
                  </a>
                </div>
                <Button size="sm" onClick={() => { setModal(item); setForm({ decision: 'APPROVED', notes: '' }) }}>Review</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={!!modal} onClose={() => setModal(null)} title="Review agent application"
        footer={<><Button variant="secondary" onClick={() => setModal(null)}>Cancel</Button>
          <Button variant={form.decision === 'APPROVED' ? 'primary' : 'danger'} onClick={review} loading={reviewMutation.isPending}
            leftIcon={form.decision === 'APPROVED' ? <CheckCircle size={13}/> : <XCircle size={13}/>}>
            {form.decision === 'APPROVED' ? 'Approve' : 'Reject'}
          </Button></>}>
        {modal && (
          <div className="space-y-4">
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>User: {modal.userId}</p>
              <p>Business: {modal.businessName || 'N/A'}</p>
            </div>
            <DocumentThumbnailGrid title="Business document" documents={[{ id: modal.id, url: modal.businessDocUrl, label: 'Business registration' }]}/>
            <Select label="Decision" required options={[{ value: 'APPROVED', label: 'Approve' }, { value: 'REJECTED', label: 'Reject' }]}
              value={form.decision} onChange={e => setForm(f => ({ ...f, decision: e.target.value }))}/>
            <Textarea label="Notes" value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}/>
          </div>
        )}
      </Modal>
    </div>
  )
}
