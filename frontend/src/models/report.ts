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

export interface OrderDrillDownItem {
  orderId: string
  email: string | null
  productId: string
  quantity: number
  status: string
  paymentMethod: string | null
  shippingCarrier: string | null
  orderCreatedAt: string
}

export interface UserDrillDownItem {
  userId: string
  username: string
  email: string
  registeredAt: string
}
