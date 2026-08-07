import { ElNotification } from 'element-plus'
import type { NotificationHandle } from 'element-plus'
import { useNotificationsStore, type NotificationEvent } from '../stores/notifications'

let socket: WebSocket | null = null

/** One saga (an order going through CREATED -> PAID -> SHIPPED -> ...) reuses a single toast, updated
 * in place at each step, instead of stacking a new card per event — a fast-moving saga was piling up
 * 4+ simultaneous notifications for one order. */
const activeByOrderId = new Map<string, NotificationHandle>()

export function useNotifications() {
  const store = useNotificationsStore()

  function connect() {
    if (socket) return
    const url = import.meta.env.VITE_NOTIFICATIONS_WS_URL
    socket = new WebSocket(url)

    socket.onmessage = (event) => {
      try {
        const parsed: NotificationEvent = JSON.parse(event.data)
        store.push(parsed)

        const key = parsed.orderId ?? parsed.eventType
        activeByOrderId.get(key)?.close()
        const handle = ElNotification({
          title: parsed.eventType.replaceAll('_', ' '),
          message: parsed.orderId ? `Order ${parsed.orderId}` : '',
          type: parsed.eventType.includes('FAILED') ? 'error' : 'success',
          duration: 4000,
          onClose: () => {
            if (activeByOrderId.get(key) === handle) activeByOrderId.delete(key)
          },
        })
        activeByOrderId.set(key, handle)
      } catch {
        // ignore malformed frames
      }
    }

    socket.onclose = () => {
      socket = null
      setTimeout(connect, 3000)
    }
  }

  return { connect, store }
}
