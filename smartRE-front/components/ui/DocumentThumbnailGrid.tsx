'use client'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import Image from 'next/image'
import { ImageOff, X, ChevronLeft, ChevronRight, FileText, ExternalLink, Download, Printer, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'

export interface DocThumb { id: string; url: string; label?: string }

const isPdf = (url: string) => {
  try { return new URL(url, 'http://x').pathname.toLowerCase().endsWith('.pdf') }
  catch { return url.toLowerCase().split('?')[0].endsWith('.pdf') }
}

const extOf = (url: string) => {
  const clean = url.split('?')[0]
  const dot = clean.lastIndexOf('.')
  return dot >= 0 ? clean.slice(dot + 1).toLowerCase() : 'bin'
}

const filenameFor = (doc: DocThumb) => `${(doc.label || 'document').toLowerCase().replace(/[^a-z0-9]+/g, '-')}.${extOf(doc.url)}`

// `doc.label` is currently always a fixed backend enum (documentCategory), so
// this is defense-in-depth rather than a live exploit — but printDocument()
// interpolates it into HTML via document.write, so it must never be trusted
// as pre-sanitized. Escape it the same way we would any user-controlled string.
const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')

async function fetchAsBlobUrl(url: string): Promise<string> {
  const res = await fetch(url, { credentials: 'include' })
  if (!res.ok) throw new Error(`Failed to fetch document (${res.status})`)
  const blob = await res.blob()
  return URL.createObjectURL(blob)
}

async function downloadDocument(doc: DocThumb) {
  try {
    const blobUrl = await fetchAsBlobUrl(doc.url)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filenameFor(doc)
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  } catch {
    toast.error('Failed to download document')
  }
}

async function printDocument(doc: DocThumb) {
  try {
    const blobUrl = await fetchAsBlobUrl(doc.url)
    const win = window.open('', '_blank')
    if (!win) { toast.error('Pop-up blocked — allow pop-ups to print'); return }
    const isPdfDoc = isPdf(doc.url)
    const safeTitle = escapeHtml(doc.label || 'Document')
    // blobUrl comes from our own URL.createObjectURL() call above, not from
    // any user input, so it's safe to interpolate as an attribute value.
    win.document.write(`<!doctype html><html><head><title>${safeTitle}</title><style>
      html,body{margin:0;height:100%;background:#525659}
      img{display:block;max-width:100%;max-height:100vh;margin:0 auto;object-fit:contain}
      embed{width:100%;height:100%;border:0}
      @media print{ html,body{background:#fff} }
    </style></head><body>
      ${isPdfDoc ? `<embed src="${blobUrl}" type="application/pdf"/>` : `<img src="${blobUrl}" onload="window.focus();window.print();" />`}
    </body></html>`)
    win.document.close()
    if (isPdfDoc) { win.onload = () => { win.focus(); win.print() } }
  } catch {
    toast.error('Failed to prepare document for printing')
  }
}

export function DocumentThumbnailGrid({ documents, title, onRemove }: { documents: DocThumb[]; title?: string; onRemove?: (doc: DocThumb) => void }) {
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
                <div className="absolute top-1.5 right-1.5 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <span role="button" tabIndex={0} onClick={e => { e.preventDefault(); e.stopPropagation(); downloadDocument(d) }}
                    aria-label="Download document" title="Download"
                    className="w-7 h-7 rounded-md bg-black/60 hover:bg-black/80 text-white flex items-center justify-center">
                    <Download size={13}/>
                  </span>
                  <span role="button" tabIndex={0} onClick={e => { e.preventDefault(); e.stopPropagation(); printDocument(d) }}
                    aria-label="Print document" title="Print"
                    className="w-7 h-7 rounded-md bg-black/60 hover:bg-black/80 text-white flex items-center justify-center">
                    <Printer size={13}/>
                  </span>
                  {onRemove && (
                    <span role="button" tabIndex={0} onClick={e => { e.preventDefault(); e.stopPropagation(); onRemove(d) }}
                      aria-label="Remove document" title="Remove"
                      className="w-7 h-7 rounded-md bg-black/60 hover:bg-red-600 text-white flex items-center justify-center">
                      <Trash2 size={13}/>
                    </span>
                  )}
                </div>
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
                <Image src={d.url} alt={d.label || 'document'} fill sizes="200px" className="object-cover" unoptimized
                  onError={() => setBroken(b => ({ ...b, [d.id]: true }))}/>
              )}
              <div className="absolute inset-0 bg-black/0 group-hover:bg-black/50 transition-colors flex items-center justify-center">
                <span className="opacity-0 group-hover:opacity-100 text-white text-[11px] font-medium transition-opacity">View full size</span>
              </div>
              <div className="absolute top-1.5 right-1.5 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <span role="button" tabIndex={0} onClick={e => { e.stopPropagation(); downloadDocument(d) }}
                  aria-label="Download document" title="Download"
                  className="w-7 h-7 rounded-md bg-black/60 hover:bg-black/80 text-white flex items-center justify-center">
                  <Download size={13}/>
                </span>
                <span role="button" tabIndex={0} onClick={e => { e.stopPropagation(); printDocument(d) }}
                  aria-label="Print document" title="Print"
                  className="w-7 h-7 rounded-md bg-black/60 hover:bg-black/80 text-white flex items-center justify-center">
                  <Printer size={13}/>
                </span>
                {onRemove && (
                  <span role="button" tabIndex={0} onClick={e => { e.stopPropagation(); onRemove(d) }}
                    aria-label="Remove document" title="Remove"
                    className="w-7 h-7 rounded-md bg-black/60 hover:bg-red-600 text-white flex items-center justify-center">
                    <Trash2 size={13}/>
                  </span>
                )}
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

      {current && createPortal(
        <div className="fixed inset-0 z-[70] bg-black/90 flex items-center justify-center p-4" onClick={() => setLightboxIndex(null)}>
          <div className="absolute top-4 right-4 flex gap-2" onClick={e => e.stopPropagation()}>
            <button onClick={() => downloadDocument(current)} aria-label="Download document" title="Download"
              className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
              <Download size={16}/>
            </button>
            <button onClick={() => printDocument(current)} aria-label="Print document" title="Print"
              className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
              <Printer size={16}/>
            </button>
            <button onClick={() => setLightboxIndex(null)} aria-label="Close"
              className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors">
              <X size={18}/>
            </button>
          </div>

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
              <Image src={current.url} alt={current.label || 'document'} fill className="object-contain" unoptimized
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
        </div>,
        document.body
      )}
    </div>
  )
}
