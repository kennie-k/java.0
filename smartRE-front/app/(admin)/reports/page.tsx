'use client'
import { Suspense, useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import Image from 'next/image'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Flag, CheckCircle, XCircle, ExternalLink, Search, Building2 } from 'lucide-react'
import { reportApi, propertyApi, userApi } from '@/lib/api'
import { queryKeys } from '@/lib/queryKeys'
import { optimisticRemoveFromPage, rollbackPage } from '@/lib/queryClientHelpers'
import type { ReportResponse, ReportStatus } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import { Modal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { InlineError } from '@/components/ui/InlineError'
import Textarea from '@/components/ui/Textarea'
import Select from '@/components/ui/Select'
import { StatusBadge } from '@/components/ui/Badge'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const TARGET_LINK: Record<string, (id: string) => string> = {
  LISTING: id => `/properties/${id}`,
  USER: id => `/sellers/${id}`,
}

const VALID_STATUSES: ReportStatus[] = ['OPEN', 'RESOLVED', 'DISMISSED']

export default function ReportsQueuePage() {
  return <Suspense fallback={<PageLoader/>}><ReportsQueuePageInner/></Suspense>
}

function ReportsQueuePageInner() {
  const qc = useQueryClient()
  const sp = useSearchParams()
  const statusParam = sp.get('status')
  const [status, setStatus] = useState<ReportStatus>(
    statusParam && VALID_STATUSES.includes(statusParam as ReportStatus) ? statusParam as ReportStatus : 'OPEN'
  )
  useEffect(() => {
    setStatus(statusParam && VALID_STATUSES.includes(statusParam as ReportStatus) ? statusParam as ReportStatus : 'OPEN')
  }, [statusParam])
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState<ReportResponse | null>(null)
  const [form, setForm] = useState({ decision: 'RESOLVED', notes: '', takeAction: false })

  const REPORTS_KEY = queryKeys.reports(status)
  const { data, isLoading: loading, error } = useQuery({
    queryKey: REPORTS_KEY,
    queryFn: () => reportApi.adminQueue(status),
  })
  const queue = data?.content ?? []
  const filtered = queue.filter(r =>
    !search
    || r.reason.toLowerCase().includes(search.toLowerCase())
    || r.details?.toLowerCase().includes(search.toLowerCase())
    || r.targetId.toLowerCase().includes(search.toLowerCase())
  )

  const evidenceQuery = useQuery({
    queryKey: queryKeys.reportEvidence(modal?.targetId ?? ''),
    queryFn: () => propertyApi.getById(modal!.targetId),
    enabled: !!modal && modal.targetType === 'LISTING',
    retry: false,
  })

  const resolveMutation = useMutation({
    mutationFn: (vars: { id: string; form: typeof form }) => reportApi.adminResolve(vars.id, vars.form),
    onMutate: vars => optimisticRemoveFromPage<ReportResponse>(qc, REPORTS_KEY, vars.id),
    onError: (_e, _v, previous) => rollbackPage(qc, REPORTS_KEY, previous),
    onSettled: () => qc.invalidateQueries({ queryKey: REPORTS_KEY }),
  })

  const resolve = async () => {
    if (!modal) return
    try {
      if (form.decision === 'RESOLVED' && form.takeAction) {
        if (modal.targetType === 'LISTING') await propertyApi.adminSuspend(modal.targetId)
        else if (modal.targetType === 'USER') await userApi.ban(modal.targetId)
      }
      await resolveMutation.mutateAsync({ id: modal.id, form })
      toast.success(form.takeAction
        ? `Report resolved — ${modal.targetType === 'LISTING' ? 'listing suspended' : 'user banned'}`
        : `Report ${form.decision.toLowerCase()}`)
      setModal(null)
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to resolve report') }
  }

  if (loading) return <PageLoader/>

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Reports</h1>
          <p className="text-muted text-[13px] mt-1">{queue.length} {status.toLowerCase()} report{queue.length !== 1 ? 's' : ''} from buyers and sellers</p>
        </div>
        <div className="w-48">
          <Select label="Status" options={[
            { value: 'OPEN', label: 'Open' },
            { value: 'RESOLVED', label: 'Resolved' },
            { value: 'DISMISSED', label: 'Dismissed' },
          ]} value={status} onChange={e => setStatus(e.target.value as ReportStatus)}/>
        </div>
      </div>

      {error && <InlineError message="Failed to load reports."/>}

      {queue.length > 0 && (
        <Input leftIcon={<Search size={15}/>} placeholder="Search by reason, details, or target ID..." value={search} onChange={e => setSearch(e.target.value)}/>
      )}

      {queue.length === 0 ? (
        <EmptyState icon={<Flag size={28}/>} title={`No ${status.toLowerCase()} reports`} desc="Reports of fake listings, scam agents, or fraudulent reviews will show up here."/>
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Search size={24}/>} title="No reports match that search"/>
      ) : (
        <div className="space-y-3">
          {filtered.map(item => {
            const linkFn = TARGET_LINK[item.targetType]
            return (
              <Card key={item.id}>
                <div className="flex items-start gap-4">
                  <div className="w-10 h-10 rounded-xl bg-red-50 dark:bg-red-500/10 text-red-500 flex items-center justify-center shrink-0">
                    <Flag size={17}/>
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <p className="font-semibold text-gray-900 dark:text-white text-[13px]">{item.reason.replace(/_/g, ' ')}</p>
                      <StatusBadge status={item.status} size="sm"/>
                    </div>
                    <p className="text-[12px] text-muted">
                      {item.targetType} · {item.targetId.slice(0, 8)}... · reported by {item.reporterId.slice(0, 8)}... · {fmt.date(item.createdAt)}
                    </p>
                    {item.details && <p className="text-[12px] text-gray-600 dark:text-gray-300 mt-1">{item.details}</p>}
                    {linkFn && (
                      <a href={linkFn(item.targetId)} target="_blank" rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-[11px] text-gold-600 dark:text-gold-400 mt-1.5 hover:underline">
                        View {item.targetType.toLowerCase()} <ExternalLink size={10}/>
                      </a>
                    )}
                  </div>
                  <Button size="sm" onClick={() => { setModal(item); setForm({ decision: 'RESOLVED', notes: '', takeAction: false }) }}>Resolve</Button>
                </div>
              </Card>
            )
          })}
        </div>
      )}

      <Modal open={!!modal} onClose={() => setModal(null)} title="Resolve report"
        footer={<><Button variant="secondary" onClick={() => setModal(null)}>Cancel</Button>
          <Button variant={form.decision === 'RESOLVED' ? 'danger' : 'secondary'} onClick={resolve} loading={resolveMutation.isPending}
            leftIcon={form.decision === 'RESOLVED' ? <CheckCircle size={13}/> : <XCircle size={13}/>}>
            {form.decision === 'RESOLVED' ? 'Mark resolved' : 'Dismiss'}
          </Button></>}>
        {modal && (
          <div className="space-y-4">
            <div className="p-3 bg-gray-50 dark:bg-[#2E2518] rounded-lg text-[13px] space-y-1">
              <p>Target: {modal.targetType} {modal.targetId}</p>
              <p>Reason: {modal.reason.replace(/_/g, ' ')}</p>
              {modal.details && <p>Details: {modal.details}</p>}
            </div>

            {modal.targetType === 'LISTING' && (
              <div className="flex items-center gap-3 p-2.5 rounded-lg border border-base">
                {evidenceQuery.isLoading ? (
                  <div className="w-14 h-14 rounded-md skeleton shrink-0"/>
                ) : evidenceQuery.data ? (
                  <>
                    <div className="relative w-14 h-14 rounded-md overflow-hidden bg-gray-100 dark:bg-[#1A1509] shrink-0">
                      {evidenceQuery.data.imageUrls?.[0] ? (
                        <Image src={evidenceQuery.data.imageUrls[0]} alt={evidenceQuery.data.title} fill sizes="56px" className="object-cover"/>
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-gold-300"><Building2 size={20}/></div>
                      )}
                    </div>
                    <div className="min-w-0">
                      <p className="text-[12px] font-medium text-gray-900 dark:text-white truncate">{evidenceQuery.data.title}</p>
                      <p className="text-[11px] text-muted">{evidenceQuery.data.county} · {fmt.currency(evidenceQuery.data.price)} · <StatusBadge status={evidenceQuery.data.status} size="sm"/></p>
                    </div>
                  </>
                ) : (
                  <p className="text-[12px] text-muted">Listing not found - it may have been deleted.</p>
                )}
              </div>
            )}

            <Select label="Decision" required options={[{ value: 'RESOLVED', label: 'Resolved (action taken)' }, { value: 'DISMISSED', label: 'Dismiss (no action needed)' }]}
              value={form.decision} onChange={e => setForm(f => ({ ...f, decision: e.target.value }))}/>

            {form.decision === 'RESOLVED' && (modal.targetType === 'LISTING' || modal.targetType === 'USER') && (
              <label className="flex items-start gap-2.5 p-2.5 rounded-lg border border-base cursor-pointer hover:bg-gray-50 dark:hover:bg-[#2E2518]">
                <input type="checkbox" className="mt-0.5" checked={form.takeAction}
                  onChange={e => setForm(f => ({ ...f, takeAction: e.target.checked }))}/>
                <span className="text-[12px] text-gray-700 dark:text-gray-300">
                  {modal.targetType === 'LISTING'
                    ? 'Also suspend this listing immediately — it will disappear from search until reactivated.'
                    : 'Also ban this user immediately — they will be unable to log in until unbanned.'}
                </span>
              </label>
            )}

            <Textarea label="Admin notes" value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}/>
          </div>
        )}
      </Modal>
    </div>
  )
}
