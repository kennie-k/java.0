'use client'
import { useState } from 'react'
import { Users, Search, ShieldCheck, ShieldPlus } from 'lucide-react'
import { useUsers } from '@/hooks/useUsers'
import type { UserResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Input from '@/components/ui/Input'
import Button from '@/components/ui/Button'
import { ConfirmModal, EmptyState, PageLoader } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'

export default function UsersPage() {
  const { users, loading, sellers, buyers, admins, promote, promotingId } = useUsers()
  const [search, setSearch] = useState('')
  const [target, setTarget] = useState<UserResponse | null>(null)

  const filtered = users.filter(u =>
    !search || u.fullName.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase())
  )

  if (loading) return <PageLoader/>

  const confirmPromote = async () => {
    if (!target) return
    const ok = await promote(target.id, target.fullName)
    if (ok) setTarget(null)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Users</h1>
        <p className="text-muted text-sm mt-1">{users.length} registered users</p>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <Card><p className="text-sm text-muted">Sellers</p><p className="font-display text-lg font-semibold mt-1">{sellers}</p></Card>
        <Card><p className="text-sm text-muted">Buyers</p><p className="font-display text-lg font-semibold mt-1">{buyers}</p></Card>
        <Card><p className="text-sm text-muted">Admins</p><p className="font-display text-lg font-semibold mt-1">{admins}</p></Card>
      </div>

      <Card>
        <Input leftIcon={<Search size={15}/>} placeholder="Search by name or email..." value={search} onChange={e => setSearch(e.target.value)} className="mb-4"/>
        {filtered.length === 0 ? (
          <EmptyState icon={<Users size={24}/>} title="No users found"/>
        ) : (
          <div className="space-y-2">
            {filtered.map(u => (
              <div key={u.id} className="flex items-center gap-3 p-3 rounded-xl hover:bg-gray-50 dark:hover:bg-[#1A1A35] transition-colors">
                <div className="w-10 h-10 rounded-full bg-gold-500 text-white flex items-center justify-center font-bold text-sm shrink-0">
                  {fmt.initials(u.fullName)}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="font-medium text-sm text-gray-900 dark:text-white">{u.fullName}</p>
                    <span className={`badge text-xs ${u.role === 'ADMIN' ? 'bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-400' : u.role === 'SELLER' ? 'bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400' : 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400'}`}>{u.role}</span>
                    {u.verified && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400 text-xs"><ShieldCheck size={10}/>Verified</span>}
                  </div>
                  <p className="text-xs text-muted">{u.email}</p>
                </div>
                <p className="text-xs text-muted shrink-0">{fmt.date(u.createdAt)}</p>
                {u.role !== 'ADMIN' && (
                  <Button size="sm" variant="secondary" leftIcon={<ShieldPlus size={13}/>} loading={promotingId === u.id} onClick={() => setTarget(u)} className="shrink-0">
                    Make admin
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      <ConfirmModal open={!!target} onClose={() => setTarget(null)} onConfirm={confirmPromote} loading={promotingId === target?.id}
        danger={false} label="Promote to admin" title="Promote to admin"
        message={target ? `Give ${target.fullName} (${target.email}) full admin access: revenue, escrow release, verification queues, and user management.` : ''}/>
    </div>
  )
}
