import { http } from './http'
import type { OrdersRevenueReport, SagaHealthReport, TopProductsReport, UserGrowthReport } from '../models'

export const reportsApi = {
  async ordersRevenue(): Promise<OrdersRevenueReport> {
    const { data } = await http.get('/api/reports/orders')
    return data
  },
  async sagaHealth(): Promise<SagaHealthReport> {
    const { data } = await http.get('/api/reports/saga-health')
    return data
  },
  async topProducts(limit = 10): Promise<TopProductsReport> {
    const { data } = await http.get('/api/reports/top-products', { params: { limit } })
    return data
  },
  async userGrowth(): Promise<UserGrowthReport> {
    const { data } = await http.get('/api/reports/user-growth')
    return data
  },
}
