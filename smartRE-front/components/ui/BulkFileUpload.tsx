'use client'
import { useEffect, useRef, useState } from 'react'
import { Upload, Loader2, CheckCircle2, XCircle, FileText, X } from 'lucide-react'
import { documentApi } from '@/lib/api'
import { cn } from '@/lib/utils'
import type { DocumentRequirementResponse } from '@/types'
import toast from 'react-hot-toast'

interface QueuedFile {
  id: string
  file: File
  category: string
  status: 'pending' | 'uploading' | 'error' | 'done'
  error?: string
  previewUrl?: string
}

interface Props {
  requiredCategories: DocumentRequirementResponse[]
  categoryLabels: Record<string, string>
  onFileRegistered: (category: string, url: string) => Promise<void>
  accept?: string
  maxConcurrent?: number
}

const MAX_SIZE = 10 * 1024 * 1024

export default function BulkFileUpload({ requiredCategories, categoryLabels, onFileRegistered, accept = '.jpg,.jpeg,.png,.pdf', maxConcurrent = 3 }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [queue, setQueue] = useState<QueuedFile[]>([])
  const [running, setRunning] = useState(false)
  const createdUrls = useRef<Set<string>>(new Set())

  useEffect(() => () => { createdUrls.current.forEach(u => URL.revokeObjectURL(u)) }, [])

  const categoryOptions = requiredCategories.length > 0 ? requiredCategories : []

  const nextCategoryFor = (offset: number, alreadyAssigned: string[]) => {
    const missing = categoryOptions.filter(r => r.isMandatory && !r.uploaded && !alreadyAssigned.includes(r.documentCategory))
    if (missing[offset]) return missing[offset].documentCategory
    const optional = categoryOptions.filter(r => !r.isMandatory && !r.uploaded && !alreadyAssigned.includes(r.documentCategory))
    if (optional[0]) return optional[0].documentCategory
    return categoryOptions[0]?.documentCategory ?? ''
  }

  const addFiles = (files: File[]) => {
    const valid = files.filter(f => {
      if (f.size > MAX_SIZE) { toast.error(`${f.name} exceeds 10MB limit`); return false }
      return true
    })
    if (valid.length === 0) return
    setQueue(q => {
      const assigned = q.map(x => x.category)
      const additions: QueuedFile[] = valid.map((file, i) => {
        let previewUrl: string | undefined
        if (file.type.startsWith('image/')) {
          previewUrl = URL.createObjectURL(file)
          createdUrls.current.add(previewUrl)
        }
        return {
          id: `${Date.now()}-${i}-${file.name}`,
          file,
          category: nextCategoryFor(i, assigned),
          status: 'pending',
          previewUrl,
        }
      })
      return [...q, ...additions]
    })
    if (inputRef.current) inputRef.current.value = ''
  }

  const setItem = (id: string, patch: Partial<QueuedFile>) => {
    setQueue(q => q.map(x => x.id === id ? { ...x, ...patch } : x))
  }

  const revokePreview = (item: QueuedFile) => {
    if (!item.previewUrl) return
    URL.revokeObjectURL(item.previewUrl)
    createdUrls.current.delete(item.previewUrl)
  }

  const removeItem = (id: string) => setQueue(q => {
    const item = q.find(x => x.id === id)
    if (item) revokePreview(item)
    return q.filter(x => x.id !== id)
  })

  const uploadOne = async (item: QueuedFile): Promise<boolean> => {
    setItem(item.id, { status: 'uploading', error: undefined })
    try {
      const res = await documentApi.upload(item.file, item.category)
      await onFileRegistered(item.category, res.url)
      setItem(item.id, { status: 'done' })
      return true
    } catch (e: any) {
      setItem(item.id, { status: 'error', error: e.response?.data?.error || 'Upload failed' })
      return false
    }
  }

  const runBatch = async () => {
    const pending = queue.filter(x => x.status === 'pending' || x.status === 'error')
    if (pending.length === 0) return
    setRunning(true)
    let cursor = 0
    let succeeded = 0
    const workers = Array.from({ length: Math.min(maxConcurrent, pending.length) }, async () => {
      while (cursor < pending.length) {
        const item = pending[cursor]
        cursor += 1
        if (await uploadOne(item)) succeeded += 1
      }
    })
    await Promise.all(workers)
    setRunning(false)
    if (succeeded === pending.length) toast.success(`${succeeded} document${succeeded === 1 ? '' : 's'} uploaded`)
    else toast.error(`${succeeded} of ${pending.length} uploaded — check the failed items below`)
    setQueue(q => {
      q.filter(x => x.status === 'done').forEach(revokePreview)
      return q.filter(x => x.status !== 'done')
    })
  }

  const pendingCount = queue.filter(x => x.status === 'pending' || x.status === 'error').length

  return (
    <div className="space-y-3">
      <div
        onClick={() => !running && inputRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={e => { e.preventDefault(); setDragOver(false); addFiles(Array.from(e.dataTransfer.files ?? [])) }}
        className={cn(
          'border-2 border-dashed rounded-xl p-6 flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors text-center',
          dragOver ? 'border-gold-500 bg-gold-50 dark:bg-gold-500/10' : 'border-gray-200 dark:border-[#1E1E3A] hover:border-gold-300',
        )}
      >
        <input ref={inputRef} type="file" accept={accept} multiple className="hidden"
          onChange={e => addFiles(Array.from(e.target.files ?? []))}/>
        <Upload size={22} className="text-gray-400"/>
        <p className="text-sm text-muted">Click or drag multiple files here</p>
        <p className="text-[11px] text-muted">JPG, PNG or PDF, up to 10MB each — select as many as you need at once</p>
      </div>

      {queue.length > 0 && (
        <div className="space-y-2">
          {queue.map(item => (
            <div key={item.id} className="flex items-center gap-2 bg-gray-50 dark:bg-[#2E2518] rounded-lg px-3 py-2">
              {item.previewUrl ? (

                <img src={item.previewUrl} alt={item.file.name}
                  className="w-9 h-9 rounded-md object-cover shrink-0 border border-gold-200 dark:border-gold-500/20"/>
              ) : (
                <div className="w-9 h-9 rounded-md shrink-0 border border-gold-200 dark:border-gold-500/20 bg-gold-50 dark:bg-gold-500/10 flex items-center justify-center">
                  <FileText size={14} className="text-gold-500"/>
                </div>
              )}
              <span className="text-[12px] truncate flex-1 min-w-0">{item.file.name}</span>
              <select
                value={item.category}
                disabled={item.status === 'uploading' || item.status === 'done'}
                onChange={e => setItem(item.id, { category: e.target.value })}
                className="input-base text-[11px] py-1 w-44 shrink-0"
              >
                {categoryOptions.map(c => (
                  <option key={c.documentCategory} value={c.documentCategory}>
                    {categoryLabels[c.documentCategory] || c.documentCategory.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
              {item.status === 'uploading' && <Loader2 size={14} className="animate-spin text-gold-500 shrink-0"/>}
              {item.status === 'done' && <CheckCircle2 size={14} className="text-emerald-500 shrink-0"/>}
              {item.status === 'error' && (
                <span title={item.error} className="shrink-0"><XCircle size={14} className="text-red-500"/></span>
              )}
              {item.status !== 'uploading' && (
                <button type="button" onClick={() => removeItem(item.id)} className="shrink-0 text-gray-400 hover:text-gray-600">
                  <X size={14}/>
                </button>
              )}
            </div>
          ))}
          <button
            type="button"
            onClick={runBatch}
            disabled={running || pendingCount === 0}
            className="btn-primary w-full text-[13px] py-2 flex items-center justify-center gap-1.5 disabled:opacity-50"
          >
            {running ? <Loader2 size={14} className="animate-spin"/> : <Upload size={14}/>}
            {running ? 'Uploading...' : `Upload ${pendingCount} file${pendingCount === 1 ? '' : 's'}`}
          </button>
        </div>
      )}
    </div>
  )
}
