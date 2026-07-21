import { useEffect, useState } from 'react'
import { paymentApi } from '@/lib/api'

const DEFAULTS = { viewingFeeKes: 200, profileAccessFeeKes: 500, transactionCommissionPct: 2.5 }

let cached: typeof DEFAULTS | null = null

export function usePlatformConfig() {
  const [config, setConfig] = useState(cached || DEFAULTS)

  useEffect(() => {
    if (cached) { setConfig(cached); return }
    paymentApi.getConfig().then(c => { cached = c; setConfig(c) }).catch(() => {})
  }, [])

  return config
}
