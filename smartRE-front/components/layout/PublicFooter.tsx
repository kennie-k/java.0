import Link from 'next/link'
import { ShieldCheck, Smartphone, Landmark, Github, Mail } from 'lucide-react'

const counties = ['Nairobi','Mombasa','Kiambu','Nakuru','Kajiado','Machakos','Uasin Gishu','Kisumu']

function WhatsAppIcon({ size = 15 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor">
      <path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38a9.9 9.9 0 0 0 4.74 1.2h.01c5.46 0 9.9-4.45 9.9-9.91 0-2.65-1.03-5.14-2.9-7.01A9.87 9.87 0 0 0 12.04 2zm5.8 14.13c-.24.68-1.4 1.32-1.93 1.38-.51.06-1.03.28-3.46-.72-2.93-1.21-4.78-4.15-4.93-4.34-.15-.2-1.18-1.57-1.18-3 0-1.42.75-2.12 1.02-2.41.27-.29.58-.36.78-.36l.55.01c.18.01.42-.07.66.5.24.58.82 2 .89 2.15.07.15.11.32.02.51-.09.19-.14.31-.27.48-.14.17-.29.37-.41.5-.14.14-.28.29-.12.57.16.28.72 1.19 1.55 1.93 1.06.95 1.96 1.24 2.24 1.38.28.14.44.12.6-.07.16-.19.69-.8.87-1.08.18-.28.36-.23.6-.14.24.09 1.54.73 1.8.86.27.14.44.2.51.32.06.11.06.68-.18 1.36z"/>
    </svg>
  )
}

export default function PublicFooter() {
  return (
    <footer className="border-t border-base bg-surface-2 mt-14">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-8">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-6 mb-6">
          <div className="col-span-2">
            <Link href="/" className="flex items-center gap-2 mb-3">
              <div className="w-7 h-7 bg-gold-500 rounded-lg flex items-center justify-center text-white font-display font-bold text-sm">S</div>
              <span className="font-display font-bold text-base text-gray-900 dark:text-white">SmartRE</span>
            </Link>
            <p className="text-[13px] text-muted leading-relaxed max-w-sm mb-4">
              Kenya&apos;s identity-verified property marketplace. Every seller passes National ID and KRA PIN checks;
              every listing is confirmed against the Ministry of Lands Ardhisasa registry before it goes live.
              Payments move through M-Pesa escrow, so buyers never pay an unverified seller.
            </p>
            <div className="flex items-center gap-3 text-[11px] text-muted">
              <span className="flex items-center gap-1"><ShieldCheck size={12} className="text-emerald-500"/>Ardhisasa-verified</span>
              <span className="flex items-center gap-1"><Smartphone size={12} className="text-emerald-500"/>M-Pesa escrow</span>
              <span className="flex items-center gap-1"><Landmark size={12} className="text-emerald-500"/>KRA + ID checked</span>
            </div>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600 mb-3">Browse</p>
            <ul className="space-y-2 text-[13px] text-muted">
              <li><Link href="/properties?listingType=SALE" className="hover:text-gold-500">Property for sale</Link></li>
              <li><Link href="/properties?listingType=RENT" className="hover:text-gold-500">Property to rent</Link></li>
              <li><Link href="/properties?propertyType=LAND" className="hover:text-gold-500">Land</Link></li>
              <li><Link href="/properties?propertyType=COMMERCIAL" className="hover:text-gold-500">Commercial</Link></li>
              <li><Link href="/properties?propertyType=APARTMENT" className="hover:text-gold-500">Apartments</Link></li>
            </ul>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600 mb-3">Popular counties</p>
            <ul className="space-y-2 text-[13px] text-muted">
              {counties.slice(0,5).map(c => (
                <li key={c}><Link href={`/properties?county=${encodeURIComponent(c)}`} className="hover:text-gold-500">{c}</Link></li>
              ))}
            </ul>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-600 mb-3">Company</p>
            <ul className="space-y-2 text-[13px] text-muted">
              <li><Link href="/#how-it-works" className="hover:text-gold-500">How it works</Link></li>
              <li><Link href="/#trust" className="hover:text-gold-500">Trust & verification</Link></li>
              <li><Link href="/register" className="hover:text-gold-500">Sell a property</Link></li>
              <li><Link href="/login" className="hover:text-gold-500">Sign in</Link></li>
            </ul>
          </div>
        </div>
        <div className="pt-5 border-t border-base flex flex-col-reverse sm:flex-row items-center justify-between gap-4">
          <p className="text-[11px] text-muted text-center sm:text-left">© {new Date().getFullYear()} SmartRE Kenya. All rights reserved.</p>
          <div className="flex items-center gap-2">
            <a href="https://github.com/kennie-afk" target="_blank" rel="noopener noreferrer" aria-label="GitHub"
              className="w-8 h-8 rounded-md border border-base flex items-center justify-center text-muted hover:text-gold-600 hover:border-gold-300 dark:hover:border-gold-500/40 transition-colors">
              <Github size={15}/>
            </a>
            <a href="mailto:kennedymwanzia001@gmail.com" aria-label="Email"
              className="w-8 h-8 rounded-md border border-base flex items-center justify-center text-muted hover:text-gold-600 hover:border-gold-300 dark:hover:border-gold-500/40 transition-colors">
              <Mail size={15}/>
            </a>
            <a href="https://wa.me/254796516595" target="_blank" rel="noopener noreferrer" aria-label="WhatsApp"
              className="w-8 h-8 rounded-md border border-base flex items-center justify-center text-muted hover:text-emerald-600 hover:border-emerald-300 dark:hover:border-emerald-500/40 transition-colors">
              <WhatsAppIcon/>
            </a>
          </div>
        </div>
      </div>
    </footer>
  )
}
