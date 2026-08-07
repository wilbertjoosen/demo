import { ElNotification } from 'element-plus'
import { useNotificationsStore, type NotificationEvent } from '../stores/notifications'

let socket: WebSocket | null = null

/** Opens (once) the live saga-event WebSocket and feeds every frame into the notifications store. */
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
        ElNotification({
          title: parsed.eventType.replaceAll('_', ' '),
          message: parsed.orderId ? `Order ${parsed.orderId}` : '',
          type: parsed.eventType.includes('FAILED') ? 'error' : 'success',
          duration: 4000,
        })
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
