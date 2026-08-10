import { http } from './http'
import type { Payment, PaymentMethodAvailability } from '../models'

export const paymentsApi = {
  async methods(): Promise<PaymentMethodAvailability> {
    const { data } = await http.get('/api/payments/methods')
    return data
  },
  async getByOrderId(orderId: string): Promise<Payment> {
    const { data } = await http.get(`/api/payments/order/${orderId}`)
    return data
  },
  async uploadProof(paymentId: string, file: File): Promise<Payment> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await http.post(`/api/payments/${paymentId}/proof`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },
}
