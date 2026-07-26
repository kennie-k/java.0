'use client'
import { useEffect, useState } from 'react'
import Image from 'next/image'
import { ImageOff, X, ChevronLeft, ChevronRight, FileText, ExternalLink } from 'lucide-react'

export interface DocThumb { id: string; url: string; label?: string }

const isPdf = (url: string) => {
  try { return new URL(url, 'http://x').pathname.toLowerCase().endsWith('.pdf') }
  catch { return url.toLowerCase().split('?')[0].endsWith('.pdf') }
}

export function DocumentThumbnailGrid({ documents, title }: { documents: DocThumb[]; title?: string }) {
  const [broken, setBroken] = useState<Record<string, boolean>>({})
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

  const imageDocs = documents.filter(d => !isPdf(d.url))

  useEffect(() => {
    document.body.style.overflow = lightboxIndex !== null ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [lightboxIndex])

  useEffect(() => {
    if (lightboxIndex === null) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLightboxIndex(null)
      if (e.key === 'ArrowLeft' && imageDocs.length > 1) setLightboxIndex(i => (i! - 1 + imageDocs.length) % imageDocs.length)
      if (e.key === 'ArrowRight' && imageDocs.length > 1) setLightboxIndex(i => (i! + 1) % imageDocs.length)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [lightboxIndex, imageDocs.length])

  if (documents.length === 0) return null

  const current = lightboxIndex !== null ? imageDocs[lightboxIndex] : null

  return (
    <div>
      <p className="text-[12px] font-medium text-gray-600 dark:text-gray-400 mb-2">
        {title ?? `Uploaded documents (${documents.length})`}
      </p>
      <div className="grid grid-cols-2 gap-2">
        {documents.map(d => {
          if (isPdf(d.url)) {
            return (
              <a key={d.id} href={d.url} target="_blank" rel="noopener noreferrer"
                className="group relative aspect-video rounded-lg overflow-hidden border border-base bg-gray-100 dark:bg-[#1A1509] flex flex-col items-center justify-center gap-1.5 text-gray-500 dark:text-gray-400 hover:border-gold-300 transition-colors">
                <FileText size={22}/>
                <span className="text-[11px] font-medium flex items-center gap-1">PDF document <ExternalLink size={10}/></span>
                {d.label && (
                  <span className="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[10px] px-2 py-1 truncate">
                    {d.label.replace(/_/g, ' ')}
                  </span>
                )}
              </a>
            )
          }
          const imgIndex = imageDocs.indexOf(d)
          return (
            <button key={d.id} type="button" onClick={() => setLightboxIndex(imgIndex)}
              className="group relative aspect-video rounded-lg overflow-hidden border border-base bg-gray-100 dark:bg-[#1A1509] text-left">
              {broken[d.id] ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-1 text-gray-400">
                  <ImageOff size={18}/>
                  <span className="text-[10px]">Failed to load</span>
                </div>
              ) : (
                <Image src={d.url} alt={d.label || 'document'} fill sizes="200px" className="object-cover"
                  onError={() => setBroken(b => ({ ...b, [d.id]: true }))}/>
              )}
              <div className="absolute inset-0 bg-black/0 group-hover:bg-black/50 transition-colors flex items-center justify-center">
                <span className="opacity-0 group-hover:opacity-100 text-white text-[11px] font-medium transition-opacity">View full size</span>
              </div>
              {d.label && (
                <span className="absolute bottom-0 inset-x-0 bg-black/60 text-white text-[10px] px-2 py-1 truncate">
                  {d.label.replace(/_/g, ' ')}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {current && (
        <div className="fixed inset-0 z-[70] bg-black/90 flex items-center justify-center p-4" onClick={() => setLightboxIndex(null)}>
          <button onClick={() => setLightboxIndex(null)} aria-label="Close"
            className="absolute top-4 right-4 w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
            <X size={18}/>
          </button>

          {imageDocs.length > 1 && (
            <button onClick={e => { e.stopPropagation(); setLightboxIndex(i => (i! - 1 + imageDocs.length) % imageDocs.length) }}
              aria-label="Previous document"
              className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
              <ChevronLeft size={20}/>
            </button>
          )}

          <div onClick={e => e.stopPropagation()} className="relative w-full max-w-3xl aspect-video">
            {broken[current.id] ? (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-gray-400">
                <ImageOff size={32}/>
                <span className="text-sm">This document failed to load</span>
              </div>
            ) : (
              <Image src={current.url} alt={current.label || 'document'} fill className="object-contain"
                onError={() => setBroken(b => ({ ...b, [current.id]: true }))}/>
            )}
          </div>

          {imageDocs.length > 1 && (
            <button onClick={e => { e.stopPropagation(); setLightboxIndex(i => (i! + 1) % imageDocs.length) }}
              aria-label="Next document"
              className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
              <ChevronRight size={20}/>
            </button>
          )}

          <p className="absolute bottom-6 inset-x-0 text-center text-white text-xs">
            {current.label?.replace(/_/g, ' ')}{current.label && imageDocs.length > 1 ? ' · ' : ''}
            {imageDocs.length > 1 ? `${lightboxIndex! + 1} / ${imageDocs.length}` : ''}
          </p>
        </div>
      )}
    </div>
  )
}
