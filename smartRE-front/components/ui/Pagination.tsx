'use client'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import Button from '@/components/ui/Button'

interface Props {
  page: number          // zero-based current page
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}

// Simple prev/next + page-count pagination used by admin list pages that are
// backed by the backend's real page/size params (see lib/api.ts) instead of
// fetching one oversized batch and filtering client-side.
export function Pagination({ page, totalPages, onPageChange, className }: Props) {
  if (totalPages <= 1) return null

  return (
    <div className={`flex items-center justify-between gap-3 pt-1 ${className || ''}`}>
      <p className="text-xs text-muted">Page {page + 1} of {totalPages}</p>
      <div className="flex items-center gap-2">
        <Button
          size="sm" variant="secondary" leftIcon={<ChevronLeft size={14}/>}
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button
          size="sm" variant="secondary" rightIcon={<ChevronRight size={14}/>}
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  )
}
