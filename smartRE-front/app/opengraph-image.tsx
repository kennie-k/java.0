import { ImageResponse } from 'next/og'

export const runtime = 'edge'
export const alt = 'SmartRE Kenya: Identity-Verified Property Marketplace'
export const size = { width: 1200, height: 630 }
export const contentType = 'image/png'

export default async function Image() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'linear-gradient(135deg, #FCFAF2 0%, #F0E3BE 50%, #E6D08F 100%)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, marginBottom: 28 }}>
          <div style={{ width: 84, height: 84, borderRadius: 20, background: '#C9A227', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 48, fontWeight: 700, color: '#FCFAF2' }}>S</div>
          <div style={{ fontSize: 64, fontWeight: 700, color: '#3A2F1F' }}>SmartRE</div>
        </div>
        <div style={{ fontSize: 30, color: '#6B5114', maxWidth: 820, textAlign: 'center' }}>
          Identity-Verified Property Marketplace for Kenya
        </div>
        <div style={{ display: 'flex', gap: 32, marginTop: 36, fontSize: 20, color: '#8C6B1A' }}>
          <div>National ID + KRA verified</div>
          <div>·</div>
          <div>Ardhisasa title checks</div>
          <div>·</div>
          <div>M-Pesa escrow</div>
        </div>
      </div>
    ),
    { ...size }
  )
}
