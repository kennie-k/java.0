import { Spinner } from '@/components/ui/Modal'

export default function Loading() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface">
      <Spinner size={36}/>
    </div>
  )
}
