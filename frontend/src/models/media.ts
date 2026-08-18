export type MediaType = 'PHOTO' | 'VIDEO' | 'DOCUMENT'

const PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp']
const VIDEO_EXTENSIONS = ['mp4', 'webm', 'mov']

/** Matches product-media-service's own allowed-extension list — kept in sync manually, both sides are small. */
export function detectMediaType(filename: string): MediaType {
  const extension = filename.split('.').pop()?.toLowerCase() ?? ''
  if (PHOTO_EXTENSIONS.includes(extension)) return 'PHOTO'
  if (VIDEO_EXTENSIONS.includes(extension)) return 'VIDEO'
  return 'DOCUMENT'
}

export interface MediaAsset {
  id: string
  productId: string
  type: MediaType
  url: string
  fileName: string
  caption: string | null
  position: number
  createdAt: string
  updatedAt: string
  deleted: boolean
}
