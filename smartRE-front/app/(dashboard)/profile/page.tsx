'use client'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Image from 'next/image'
import { User, Mail, Phone, Shield, ShieldCheck, Save, Landmark, Building2, Lock } from 'lucide-react'
import { userApi, authApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import FileUpload from '@/components/ui/FileUpload'
import { Modal } from '@/components/ui/Modal'
import { fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const payoutMethods = [
  { value: 'MPESA', label: 'M-Pesa phone' },
  { value: 'PAYBILL', label: 'Paybill' },
  { value: 'TILL', label: 'Till number' },
  { value: 'BANK', label: 'Bank transfer' },
]

export default function ProfilePage() {
  const { user, setUser, logout } = useAuthStore()
  const router = useRouter()
  const [form, setForm] = useState({ fullName: '', phone: '', profileImage: '' })
  const [payout, setPayout] = useState({
    accountType: 'INDIVIDUAL', companyName: '', companyRegNumber: '', kraPin: '',
    preferredPayoutMethod: 'MPESA', payoutPhone: '', paybillNumber: '', tillNumber: '',
    bankAccountName: '', bankAccountNumber: '', bankName: '', bankBranch: '', bankSwiftCode: '',
  })
  const [saving, setSave] = useState(false)
  const [savingPayout, setSavePayout] = useState(false)
  const [pwModal, setPwModal] = useState(false)
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [changingPw, setChangingPw] = useState(false)

  useEffect(() => {
    if (user) setForm(f => ({ ...f, fullName: user.fullName }))
    userApi.me().then(u => {
      setForm(f => ({ ...f, phone: u.phone || '', profileImage: u.profileImage || '' }))
      setPayout({
        accountType: u.accountType || 'INDIVIDUAL',
        companyName: u.companyName || '', companyRegNumber: u.companyRegNumber || '', kraPin: u.kraPin || '',
        preferredPayoutMethod: u.preferredPayoutMethod || 'MPESA', payoutPhone: u.payoutPhone || '',
        paybillNumber: u.paybillNumber || '', tillNumber: u.tillNumber || '',
        bankAccountName: u.bankAccountName || '', bankAccountNumber: u.bankAccountNumber || '',
        bankName: u.bankName || '', bankBranch: u.bankBranch || '', bankSwiftCode: u.bankSwiftCode || '',
      })
    }).catch(() => {})
  }, [user])

  const save = async () => {
    setSave(true)
    try {
      await userApi.updateMe(form)
      if (user) setUser({ ...user, fullName: form.fullName })
      toast.success('Profile updated')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Update failed') }
    finally { setSave(false) }
  }

  const savePayoutDetails = async () => {
    setSavePayout(true)
    try {
      await userApi.updateMe(payout)
      toast.success('Payout details saved')
    } catch (e: any) { toast.error(e.response?.data?.error || 'Update failed') }
    finally { setSavePayout(false) }
  }

  const changePassword = async () => {
    if (pwForm.newPassword !== pwForm.confirm) { toast.error('New passwords do not match'); return }
    setChangingPw(true)
    try {
      await userApi.changePassword({ currentPassword: pwForm.currentPassword, newPassword: pwForm.newPassword })
      toast.success('Password changed')
      setPwModal(false)
      setPwForm({ currentPassword: '', newPassword: '', confirm: '' })
    } catch (e: any) { toast.error(e.response?.data?.error || 'Failed to change password') }
    finally { setChangingPw(false) }
  }

  const handleLogout = async () => {
    try { await authApi.logout() } catch {}
    logout(); router.push('/login')
  }

  if (!user) return null

  return (
    <div className="max-w-2xl mx-auto space-y-5">
      <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">Profile</h1>

      <Card>
        <div className="flex items-center gap-3.5">
          <div className="relative shrink-0">
            <div className="w-16 h-16 rounded-xl bg-gold-500 text-white flex items-center justify-center font-display font-bold text-lg overflow-hidden relative">
              {form.profileImage ? (
                <Image src={form.profileImage} alt={user.fullName} fill sizes="64px" className="object-cover"/>
              ) : fmt.initials(user.fullName)}
            </div>
          </div>
          <div className="flex-1 min-w-0">
            <h2 className="font-display text-base font-semibold text-gray-900 dark:text-white truncate">{user.fullName}</h2>
            <p className="text-muted text-[12px] truncate">{user.email}</p>
            <div className="flex items-center gap-1.5 mt-1.5">
              <span className="badge bg-gold-50 text-gold-600 dark:bg-gold-500/10 dark:text-gold-400">{user.role}</span>
              {user.verified && <span className="badge bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400"><ShieldCheck size={10}/>Verified</span>}
            </div>
          </div>
        </div>
        <div className="mt-3.5">
          <FileUpload category="PROFILE_IMAGE" compact accept=".jpg,.jpeg,.png" label="Change profile photo"
            onUploaded={url => setForm(f => ({ ...f, profileImage: url }))}/>
        </div>
      </Card>

      <Card>
        <h2 className="font-display font-semibold text-[13px] mb-3.5">Account details</h2>
        <div className="space-y-3">
          <Input label="Full name" leftIcon={<User size={14}/>} value={form.fullName} onChange={e => setForm(f => ({...f, fullName: e.target.value}))}/>
          <Input label="Email address" leftIcon={<Mail size={14}/>} value={user.email} disabled className="opacity-60"/>
          <Input label="Phone number" leftIcon={<Phone size={14}/>} value={form.phone} onChange={e => setForm(f => ({...f, phone: e.target.value}))} placeholder="0712 345 678"/>
          <Button onClick={save} loading={saving} leftIcon={<Save size={13}/>}>Save changes</Button>
        </div>
      </Card>

      {user.role === 'SELLER' && (
        <Card>
          <h2 className="font-display font-semibold text-[13px] mb-1 flex items-center gap-1.5"><Landmark size={14} className="text-gold-500"/>Payout details</h2>
          <p className="text-[11px] text-muted mb-3.5">Where escrow funds are sent when an admin releases a completed sale.</p>
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-2">
              {(['INDIVIDUAL', 'COMPANY'] as const).map(t => (
                <button key={t} type="button" onClick={() => setPayout(p => ({ ...p, accountType: t }))}
                  className={`flex items-center gap-2 border-2 rounded-md px-3 py-2 text-[12px] font-medium transition-all ${payout.accountType === t ? 'border-gold-500 bg-gold-50 dark:bg-gold-500/10 text-gold-700 dark:text-gold-400' : 'border-gray-200 dark:border-[#3A2F1F] text-gray-600 dark:text-gray-300'}`}>
                  {t === 'INDIVIDUAL' ? <User size={13}/> : <Building2 size={13}/>}{t === 'INDIVIDUAL' ? 'Individual' : 'Company'}
                </button>
              ))}
            </div>

            {payout.accountType === 'COMPANY' && (
              <>
                <Input label="Company name" value={payout.companyName} onChange={e => setPayout(p => ({ ...p, companyName: e.target.value }))}/>
                <Input label="Company registration number" placeholder="CPR/2019/12345" value={payout.companyRegNumber} onChange={e => setPayout(p => ({ ...p, companyRegNumber: e.target.value }))}/>
              </>
            )}
            <Input label="KRA PIN" placeholder="A123456789Z" value={payout.kraPin} onChange={e => setPayout(p => ({ ...p, kraPin: e.target.value }))}/>

            <div>
              <label className="text-[11px] font-medium text-gray-600 dark:text-gray-400 block mb-1">Preferred payout method</label>
              <select value={payout.preferredPayoutMethod} onChange={e => setPayout(p => ({ ...p, preferredPayoutMethod: e.target.value }))} className="input-base">
                {payoutMethods.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
            </div>

            {payout.preferredPayoutMethod === 'MPESA' && (
              <Input label="M-Pesa payout phone" placeholder="254712345678" value={payout.payoutPhone} onChange={e => setPayout(p => ({ ...p, payoutPhone: e.target.value }))}/>
            )}
            {payout.preferredPayoutMethod === 'PAYBILL' && (
              <>
                <Input label="Paybill number" value={payout.paybillNumber} onChange={e => setPayout(p => ({ ...p, paybillNumber: e.target.value }))}/>
                <Input label="Account number" value={payout.bankAccountNumber} onChange={e => setPayout(p => ({ ...p, bankAccountNumber: e.target.value }))}/>
              </>
            )}
            {payout.preferredPayoutMethod === 'TILL' && (
              <Input label="Till number" value={payout.tillNumber} onChange={e => setPayout(p => ({ ...p, tillNumber: e.target.value }))}/>
            )}
            {payout.preferredPayoutMethod === 'BANK' && (
              <>
                <Input label="Account name" value={payout.bankAccountName} onChange={e => setPayout(p => ({ ...p, bankAccountName: e.target.value }))}/>
                <Input label="Account number" value={payout.bankAccountNumber} onChange={e => setPayout(p => ({ ...p, bankAccountNumber: e.target.value }))}/>
                <Input label="Bank name" placeholder="Equity Bank Kenya" value={payout.bankName} onChange={e => setPayout(p => ({ ...p, bankName: e.target.value }))}/>
                <Input label="Branch" value={payout.bankBranch} onChange={e => setPayout(p => ({ ...p, bankBranch: e.target.value }))}/>
                <Input label="SWIFT code" placeholder="EQBLKENA" value={payout.bankSwiftCode} onChange={e => setPayout(p => ({ ...p, bankSwiftCode: e.target.value }))}/>
              </>
            )}
            <Button onClick={savePayoutDetails} loading={savingPayout} leftIcon={<Save size={13}/>}>Save payout details</Button>
          </div>
        </Card>
      )}

      <Card>
        <h2 className="font-display font-semibold text-[13px] mb-3.5 flex items-center gap-1.5"><Shield size={14} className="text-gold-500"/>Security</h2>
        <div className="flex items-center justify-between py-2.5 border-b border-base">
          <div><p className="font-medium text-[12px]">Password</p><p className="text-[11px] text-muted">Change your account password</p></div>
          <Button variant="secondary" size="sm" leftIcon={<Lock size={12}/>} onClick={() => setPwModal(true)}>Change</Button>
        </div>
        <div className="flex items-center justify-between py-2.5">
          <div><p className="font-medium text-[12px] text-red-600 dark:text-red-400">Sign out</p><p className="text-[11px] text-muted">Log out of your account</p></div>
          <Button variant="danger" size="sm" onClick={handleLogout}>Sign out</Button>
        </div>
      </Card>

      <Modal open={pwModal} onClose={() => setPwModal(false)} title="Change password"
        footer={<><Button variant="secondary" onClick={() => setPwModal(false)}>Cancel</Button><Button onClick={changePassword} loading={changingPw}>Update password</Button></>}>
        <div className="space-y-3">
          <Input label="Current password" type="password" value={pwForm.currentPassword} onChange={e => setPwForm(f => ({ ...f, currentPassword: e.target.value }))} leftIcon={<Lock size={14}/>}/>
          <Input label="New password" type="password" placeholder="Min. 8 characters" value={pwForm.newPassword} onChange={e => setPwForm(f => ({ ...f, newPassword: e.target.value }))} leftIcon={<Lock size={14}/>}/>
          <Input label="Confirm new password" type="password" value={pwForm.confirm} onChange={e => setPwForm(f => ({ ...f, confirm: e.target.value }))} leftIcon={<Lock size={14}/>}/>
        </div>
      </Modal>
    </div>
  )
}
