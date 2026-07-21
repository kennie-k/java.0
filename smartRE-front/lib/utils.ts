import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'
import { format, formatDistanceToNow } from 'date-fns'

export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs))

export const fmt = {
  currency: (n: number, cur = 'KES') =>
    `${cur} ${n.toLocaleString('en-KE', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`,
  date: (d?: string | null) => { try { return d ? format(new Date(d), 'MMM d, yyyy') : 'N/A' } catch { return 'N/A' } },
  datetime: (d?: string | null) => { try { return d ? format(new Date(d), 'MMM d, yyyy · h:mm a') : 'N/A' } catch { return 'N/A' } },
  ago: (d?: string | null) => { try { return d ? formatDistanceToNow(new Date(d), { addSuffix: true }) : 'N/A' } catch { return 'N/A' } },
  initials: (name: string) => name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2),
  phone: (p: string) => p.startsWith('254') ? `+${p}` : p,
}

export const statusVariant = (s: string): 'success'|'warning'|'error'|'info'|'muted' => {
  const map: Record<string, 'success'|'warning'|'error'|'info'|'muted'> = {
    ACTIVE:'success', APPROVED:'success', COMPLETED:'success', CONFIRMED:'success', PAID:'success', PAYOUT_COMPLETED:'success',
    DRAFT:'muted', SOLD:'muted', RENTED:'muted', WITHDRAWN:'muted',
    PENDING:'warning', PENDING_FEE:'warning', PENDING_VERIFICATION:'warning', STK_PUSHED:'warning', SUBMITTED:'warning', HUMAN_REVIEW:'warning', REQUIRES_RESUBMISSION:'warning', SUSPENDED:'warning',
    REQUESTED:'info', AI_SCREENING:'info', DOCUMENTS_UPLOADED:'info', MINISTRY_LANDS_CHECK:'info', ENCUMBRANCE_CHECK:'info', LEGAL_REVIEW:'info',
    FAILED:'error', REJECTED:'error', CANCELLED:'error', PAYOUT_FAILED:'error', EXPIRED:'error', NO_SHOW:'error', REFUNDED:'error',
  }
  return map[s] ?? 'muted'
}

export const statusLabel = (s: string) => ({
  PENDING_FEE:'Awaiting Fee', PENDING_VERIFICATION:'Pending Verification',
  STK_PUSHED:'Awaiting PIN', PAYOUT_FAILED:'Payout Failed', PAYOUT_COMPLETED:'Paid Out',
  HUMAN_REVIEW:'Under Review', AI_SCREENING:'AI Screening', REQUIRES_RESUBMISSION:'Needs Resubmission',
  DOCUMENTS_UPLOADED:'Docs Uploaded', MINISTRY_LANDS_CHECK:'Ministry of Lands Check', ENCUMBRANCE_CHECK:'Encumbrance Check',
  LEGAL_REVIEW:'Legal Review', NO_SHOW:'No Show',
}[s] ?? s.replace(/_/g,' ').replace(/\b\w/g, c => c.toUpperCase()))
