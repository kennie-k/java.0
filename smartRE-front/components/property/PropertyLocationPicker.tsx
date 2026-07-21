'use client'
import { useCallback, useState } from 'react'
import { useJsApiLoader, GoogleMap, MarkerF } from '@react-google-maps/api'
import { Crosshair, MapPinOff } from 'lucide-react'
import Button from '@/components/ui/Button'

const API_KEY = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY || ''
const NAIROBI = { lat: -1.286389, lng: 36.817223 }

const mapContainerStyle = { width: '100%', height: '100%', borderRadius: '0.75rem' }

const mapOptions: google.maps.MapOptions = {
  disableDefaultUI: true,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: false,
}

interface Props {
  latitude?: number
  longitude?: number
  onChange: (lat: number, lng: number) => void
}

export default function PropertyLocationPicker({ latitude, longitude, onChange }: Props) {
  const { isLoaded } = useJsApiLoader({ id: 'smartre-google-maps', googleMapsApiKey: API_KEY })
  const [locating, setLocating] = useState(false)

  const center = typeof latitude === 'number' && typeof longitude === 'number' ? { lat: latitude, lng: longitude } : NAIROBI

  const handleClick = useCallback((e: google.maps.MapMouseEvent) => {
    if (e.latLng) onChange(e.latLng.lat(), e.latLng.lng())
  }, [onChange])

  const handleDragEnd = useCallback((e: google.maps.MapMouseEvent) => {
    if (e.latLng) onChange(e.latLng.lat(), e.latLng.lng())
  }, [onChange])

  const useMyLocation = () => {
    if (!navigator.geolocation) return
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      pos => { onChange(pos.coords.latitude, pos.coords.longitude); setLocating(false) },
      () => setLocating(false),
      { timeout: 8000 }
    )
  }

  if (!API_KEY) {
    return (
      <div className="h-56 rounded-xl bg-gray-50 dark:bg-white/5 border border-dashed border-base flex flex-col items-center justify-center gap-2 text-center px-4">
        <MapPinOff size={22} className="text-gray-400"/>
        <p className="text-[12px] text-muted">Map picker unavailable</p>
        <p className="text-[10px] text-muted">Set NEXT_PUBLIC_GOOGLE_MAPS_API_KEY to enable</p>
      </div>
    )
  }

  if (!isLoaded) {
    return <div className="h-56 rounded-xl bg-gray-100 dark:bg-white/5 animate-pulse"/>
  }

  return (
    <div className="space-y-2">
      <div className="h-56 relative">
        <GoogleMap mapContainerStyle={mapContainerStyle} center={center} zoom={typeof latitude === 'number' ? 15 : 11} options={mapOptions} onClick={handleClick}>
          {typeof latitude === 'number' && typeof longitude === 'number' && (
            <MarkerF position={center} draggable onDragEnd={handleDragEnd}/>
          )}
        </GoogleMap>
      </div>
      <div className="flex items-center justify-between">
        <p className="text-[11px] text-muted">Click the map or drag the pin to set the exact location</p>
        <Button type="button" size="sm" variant="ghost" leftIcon={<Crosshair size={13}/>} loading={locating} onClick={useMyLocation}>
          Use my location
        </Button>
      </div>
    </div>
  )
}
