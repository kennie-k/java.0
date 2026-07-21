'use client'
import { useRef, useState } from 'react'
import { Upload, Loader2 } from 'lucide-react'
import { documentApi } from '@/lib/api'
import { cn } from '@/lib/utils'
import toast from 'react-hot-toast'

interface Props {
  category: string
  onUploaded: (url: string) => void
  accept?: string
  label?: string
  compact?: boolean
}

export default function FileUpload({ category, onUploaded, accept = '.jpg,.jpeg,.png,.pdf', label = 'Click or drag a file here', compact }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)

  const handleFile = async (file: File | undefined) => {
    if (!file) return
    if (file.size > 10 * 1024 * 1024) { toast.error('File exceeds 10MB limit'); return }
    setUploading(true)
    try {
      const res = await documentApi.upload(file, category)
      onUploaded(res.url)
      toast.success('File uploaded')
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Upload failed')
    } finally {
      setUploading(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div
      onClick={() => !uploading && inputRef.current?.click()}
      onDragOver={e => { e.preventDefault(); setDragOver(true) }}
      onDragLeave={() => setDragOver(false)}
      onDrop={e => { e.preventDefault(); setDragOver(false); handleFile(e.dataTransfer.files?.[0]) }}
      className={cn(
        'border-2 border-dashed rounded-xl flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors text-center',
        compact ? 'p-4' : 'p-8',
        dragOver ? 'border-gold-500 bg-gold-50 dark:bg-gold-500/10' : 'border-gray-200 dark:border-[#1E1E3A] hover:border-gold-300',
      )}
    >
      <input ref={inputRef} type="file" accept={accept} className="hidden" onChange={e => handleFile(e.target.files?.[0])}/>
      {uploading ? (
        <Loader2 size={compact ? 18 : 24} className="animate-spin text-gold-500"/>
      ) : (
        <Upload size={compact ? 18 : 24} className="text-gray-400"/>
      )}
      <p className={cn('text-muted', compact ? 'text-[11px]' : 'text-sm')}>{uploading ? 'Uploading...' : label}</p>
      {!compact && <p className="text-[11px] text-muted">JPG, PNG or PDF, up to 10MB</p>}
    </div>
  )
}
