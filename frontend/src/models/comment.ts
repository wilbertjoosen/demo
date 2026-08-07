export interface Comment {
  id: string
  productId: string
  parentId: string | null
  keycloakUserId: string
  authorName: string
  body: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}
