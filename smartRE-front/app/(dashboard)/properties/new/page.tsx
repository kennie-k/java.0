'use client'
import { useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { X, ImageIcon, MapPin } from 'lucide-react'
import { propertyApi } from '@/lib/api'
import { useAuthGuard } from '@/hooks/useAuthGuard'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import FileUpload from '@/components/ui/FileUpload'
import PropertyLocationPicker from '@/components/property/PropertyLocationPicker'
import toast from 'react-hot-toast'

const schema = z.object({
  title:        z.string().min(5,'Title must be at least 5 characters'),
  description:  z.string().optional(),
  propertyType: z.enum(['HOUSE','APARTMENT','LAND','COMMERCIAL','VILLA']),
  listingType:  z.enum(['SALE','RENT']),
  county:       z.string().min(2,'County required'),
  subCounty:    z.string().optional(),
  city:         z.string().optional(),
  price:        z.coerce.number().min(1,'Price required'),
  bedrooms:     z.coerce.number().optional(),
  bathrooms:    z.coerce.number().optional(),
  areaSqm:      z.coerce.number().optional(),
  yearBuilt:    z.coerce.number().optional(),
})
type Form = z.infer<typeof schema>

export default function NewPropertyPage() {
  const router = useRouter()
  const { ready } = useAuthGuard(['SELLER'], '/dashboard')
  const { register, handleSubmit, formState:{ errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema), defaultValues:{ propertyType:'HOUSE', listingType:'SALE' }
  })
  const [imageUrls, setImageUrls] = useState<string[]>([])
  const [coords, setCoords] = useState<{ lat?: number; lng?: number }>({})

  const onSubmit = async (d: Form) => {
    try {
      const p = await propertyApi.create({ ...d, imageUrls, latitude: coords.lat, longitude: coords.lng })
      toast.success('Property listed successfully!')
      router.push(`/properties/${p.id}`)
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Failed to create listing')
    }
  }

  if (!ready) return null

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="font-display text-lg font-semibold text-gray-900 dark:text-white">List a property</h1>
        <p className="text-muted text-sm mt-1">Fill in the details below. Your listing will be in DRAFT until you complete identity verification.</p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <h2 className="font-display font-semibold mb-4">Basic details</h2>
          <div className="space-y-4">
            <Input label="Property title" placeholder="e.g. 3 Bedroom House in Westlands" required {...register('title')} error={errors.title?.message}/>
            <Textarea label="Description" placeholder="Describe the property in detail..." {...register('description')}/>
            <div className="grid grid-cols-2 gap-4">
              <Select label="Property type" required options={[
                {value:'HOUSE',label:'House'},{value:'APARTMENT',label:'Apartment'},
                {value:'LAND',label:'Land'},{value:'COMMERCIAL',label:'Commercial'},{value:'VILLA',label:'Villa'},
              ]} {...register('propertyType')} error={errors.propertyType?.message}/>
              <Select label="Listing type" required options={[
                {value:'SALE',label:'For Sale'},{value:'RENT',label:'For Rent'},
              ]} {...register('listingType')} error={errors.listingType?.message}/>
            </div>
          </div>
        </Card>

        <Card>
          <h2 className="font-display font-semibold mb-4 flex items-center gap-2"><ImageIcon size={17} className="text-gold-500"/>Photos</h2>
          <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 mb-3">
            {imageUrls.map((url, i) => (
              <div key={url} className="relative aspect-square rounded-lg overflow-hidden group">
                <Image src={url} alt={`Property photo ${i + 1}`} fill sizes="200px" className="object-cover"/>
                <button type="button" onClick={() => setImageUrls(u => u.filter((_, idx) => idx !== i))}
                  className="absolute top-1 right-1 w-6 h-6 rounded-full bg-black/60 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity" aria-label="Remove photo">
                  <X size={13}/>
                </button>
              </div>
            ))}
          </div>
          <FileUpload category="PROPERTY_IMAGE" accept=".jpg,.jpeg,.png" label="Add a photo" compact
            onUploaded={url => setImageUrls(u => [...u, url])}/>
        </Card>

        <Card>
          <h2 className="font-display font-semibold mb-4">Location</h2>
          <div className="space-y-4">
            <Input label="County" placeholder="e.g. Nairobi" required {...register('county')} error={errors.county?.message}/>
            <div className="grid grid-cols-2 gap-4">
              <Input label="Sub-county" placeholder="e.g. Westlands" {...register('subCounty')}/>
              <Input label="City / Area" placeholder="e.g. Westlands" {...register('city')}/>
            </div>
            <div>
              <label className="text-xs font-medium text-gray-600 dark:text-gray-400 flex items-center gap-1 mb-2"><MapPin size={12}/>Pin the exact location</label>
              <PropertyLocationPicker latitude={coords.lat} longitude={coords.lng} onChange={(lat, lng) => setCoords({ lat, lng })}/>
            </div>
          </div>
        </Card>

        <Card>
          <h2 className="font-display font-semibold mb-4">Pricing & details</h2>
          <div className="space-y-4">
            <Input label="Price (KES)" type="number" placeholder="15000000" required {...register('price')} error={errors.price?.message}/>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <Input label="Bedrooms" type="number" min={0} {...register('bedrooms')}/>
              <Input label="Bathrooms" type="number" min={0} {...register('bathrooms')}/>
              <Input label="Area (m²)" type="number" min={0} {...register('areaSqm')}/>
              <Input label="Year built" type="number" min={1900} max={2024} {...register('yearBuilt')}/>
            </div>
          </div>
        </Card>

        <div className="flex gap-3 justify-end">
          <Button variant="secondary" type="button" onClick={() => router.back()}>Cancel</Button>
          <Button type="submit" loading={isSubmitting}>Create listing</Button>
        </div>
      </form>
    </div>
  )
}
