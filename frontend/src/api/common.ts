import { http } from './http'
import { unwrapCollection } from './hal'
import type { Country } from '../models'

export const commonApi = {
  async countries(): Promise<Country[]> {
    const { data } = await http.get('/api/common/countries')
    return unwrapCollection<Country>(data)
  },
}
