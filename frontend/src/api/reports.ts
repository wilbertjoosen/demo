import { http } from './http'
import type {
  OrdersRevenueReport,
  SagaHealthReport,
  TopProductsReport,
  UserGrowthReport,
  OrderDrillDownItem,
  UserDrillDownItem,
} from '../models'

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
  async ordersDrillDown(params: { status?: string; date?: string; stage?: string }): Promise<OrderDrillDownItem[]> {
    const { data } = await http.get('/api/reports/orders/drill-down', { params })
    return data
  },
  async usersDrillDown(params: { date?: string }): Promise<UserDrillDownItem[]> {
    const { data } = await http.get('/api/reports/users/drill-down', { params })
    return data
  },
}
