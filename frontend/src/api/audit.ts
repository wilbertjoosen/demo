import { http } from './http'
import type { RecordHistoryEntry } from '../models'

export const auditApi = {
  async recordHistory(recordId: string): Promise<RecordHistoryEntry[]> {
    const { data } = await http.get(`/api/audit/records/${recordId}/history`)
    return data
  },
}
