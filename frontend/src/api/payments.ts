import { http } from './http'
import type { PaymentMethodAvailability } from '../models'

export const paymentsApi = {
  async methods(): Promise<PaymentMethodAvailability> {
    const { data } = await http.get('/api/payments/methods')
    return data
  },
}
