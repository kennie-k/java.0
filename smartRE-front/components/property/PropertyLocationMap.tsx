'use client'
import { useJsApiLoader, GoogleMap, MarkerF } from '@react-google-maps/api'
import { MapPin, MapPinOff } from 'lucide-react'

const API_KEY = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY || ''

const mapContainerStyle = { width: '100%', height: '100%', borderRadius: '0.75rem' }

const mapOptions: google.maps.MapOptions = {
  disableDefaultUI: true,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: false,
  fullscreenControl: true,
}

interface Props {
  latitude?: number
  longitude?: number
  title?: string
  fallbackLabel?: string
  heightClass?: string
}

export default function PropertyLocationMap({ latitude, longitude, title, fallbackLabel, heightClass = 'h-64' }: Props) {
  const { isLoaded } = useJsApiLoader({ id: 'smartre-google-maps', googleMapsApiKey: API_KEY })

  if (!API_KEY) {
    return (
      <div className={`${heightClass} rounded-xl bg-gray-50 dark:bg-white/5 border border-dashed border-base flex flex-col items-center justify-center gap-2 text-center px-4`}>
        <MapPinOff size={22} className="text-gray-400"/>
        <p className="text-[12px] text-muted">{fallbackLabel || 'Map unavailable'}</p>
        <p className="text-[10px] text-muted">Set NEXT_PUBLIC_GOOGLE_MAPS_API_KEY to enable maps</p>
      </div>
    )
  }

  if (typeof latitude !== 'number' || typeof longitude !== 'number') {
    return (
      <div className={`${heightClass} rounded-xl bg-gray-50 dark:bg-white/5 border border-dashed border-base flex flex-col items-center justify-center gap-2 text-center px-4`}>
        <MapPin size={22} className="text-gray-400"/>
        <p className="text-[12px] text-muted">{fallbackLabel || 'Exact location not pinned yet'}</p>
      </div>
    )
  }

  if (!isLoaded) {
    return <div className={`${heightClass} rounded-xl bg-gray-100 dark:bg-white/5 animate-pulse`}/>
  }

  const center = { lat: latitude, lng: longitude }

  return (
    <div className={heightClass}>
      <GoogleMap mapContainerStyle={mapContainerStyle} center={center} zoom={15} options={mapOptions}>
        <MarkerF position={center} title={title}/>
      </GoogleMap>
    </div>
  )
}
