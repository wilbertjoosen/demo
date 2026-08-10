export interface DailyCount {
  date: string
  count: number
}

export interface OrdersRevenueReport {
  totalOrders: number
  totalRevenue: number
  byStatus: Record<string, number>
  dailyOrderCounts: DailyCount[]
}

export interface SagaHealthReport {
  inProgressCount: number
  completedCount: number
  cancelledCount: number
  cancellationRate: number
  avgTimeToConfirmationMinutes: number | null
  failuresByStage: Record<string, number>
}

export interface ProductStat {
  productId: string
  name: string
  sku: string
  active: boolean
  totalQuantityOrdered: number
  orderCount: number
  revenue: number
}

export interface TopProductsReport {
  products: ProductStat[]
}

export interface UserGrowthReport {
  totalNewUsers: number
  dailyRegistrations: DailyCount[]
}
