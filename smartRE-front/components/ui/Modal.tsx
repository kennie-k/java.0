'use client'
import { useEffect, ReactNode } from 'react'
import { X } from 'lucide-react'
import Button from './Button'

const sizes = { sm:400, md:560, lg:720, xl:900 }

interface ModalProps {
  open:boolean; onClose():void; title?:string; children:ReactNode
  size?:'sm'|'md'|'lg'|'xl'; footer?:ReactNode
}
export function Modal({ open, onClose, title, children, size='md', footer }: ModalProps) {
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [open])
  useEffect(() => {
    const h = (e:KeyboardEvent) => { if (e.key==='Escape') onClose() }
    window.addEventListener('keydown',h); return () => window.removeEventListener('keydown',h)
  }, [onClose])
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{background:'rgba(0,0,0,0.55)',backdropFilter:'blur(4px)'}} onClick={e=>{if(e.target===e.currentTarget)onClose()}}>
      <div className="animate-slide-up card flex flex-col overflow-hidden w-full" style={{maxWidth:sizes[size],maxHeight:'90vh'}}>
        {title && (
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-[#1E1E3A] shrink-0">
            <h2 className="font-display text-lg font-semibold">{title}</h2>
            <button onClick={onClose} className="w-8 h-8 rounded-lg hover:bg-gray-100 dark:hover:bg-[#1A1A35] flex items-center justify-center text-gray-400 transition-colors" aria-label="Close"><X size={16}/></button>
          </div>
        )}
        <div className="flex-1 overflow-auto p-6">{children}</div>
        {footer && <div className="flex gap-3 justify-end px-6 py-4 border-t border-gray-200 dark:border-[#1E1E3A] shrink-0">{footer}</div>}
      </div>
    </div>
  )
}

export function ConfirmModal({ open,onClose,onConfirm,title,message,label='Confirm',loading,danger=true }:
  { open:boolean;onClose():void;onConfirm():void;title:string;message:string;label?:string;loading?:boolean;danger?:boolean }) {
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm" footer={
      <><Button variant="secondary" onClick={onClose}>Cancel</Button>
      <Button variant={danger?'danger':'primary'} onClick={onConfirm} loading={loading}>{label}</Button></>
    }>
      <p className="text-gray-600 dark:text-gray-300">{message}</p>
    </Modal>
  )
}

export function EmptyState({ icon, title, desc, action }:{ icon?:ReactNode;title:string;desc?:string;action?:ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
      {icon && <div className="w-14 h-14 rounded-2xl bg-gold-50 dark:bg-gold-500/10 text-gold-500 flex items-center justify-center mb-4">{icon}</div>}
      <h3 className="font-display text-lg font-semibold mb-2">{title}</h3>
      {desc && <p className="text-sm text-muted mb-6 max-w-sm">{desc}</p>}
      {action}
    </div>
  )
}

export function Spinner({ size=24, className='' }:{ size?:number; className?:string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={`animate-spin ${className}`}><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" opacity=".2"/><path d="M12 2a10 10 0 0 1 10 10" stroke="#C9A227" strokeWidth="2" strokeLinecap="round"/></svg>
}

export function PageLoader() {
  return <div className="flex items-center justify-center min-h-[60vh]"><Spinner size={40}/></div>
}

export function SkeletonCard() {
  return <div className="card p-4 space-y-2.5"><div className="skeleton h-3 w-2/3 rounded"/><div className="skeleton h-5 w-1/2 rounded"/><div className="skeleton h-6 w-full rounded mt-2"/></div>
}
