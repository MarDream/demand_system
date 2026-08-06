import { ref, onUnmounted } from 'vue'
import { getToken } from '@/utils/auth'

export interface CollaborationCursor {
  userId: number
  userName: string
  tableId: number
  recordId: number
  fieldId: number
  timestamp: number
}

export interface OnlineCollaborator {
  id: number
  name: string
  avatar?: string
}

export interface PresenceUpdatedEvent {
  type: 'presence_updated'
  users: OnlineCollaborator[]
}

export interface CellUpdateEvent {
  type: 'cell_updated'
  tableId: number
  recordId: number
  fieldId: number
  value: unknown
  userId: number
  userName: string
  version: number
}

export interface ConflictEvent {
  type: 'conflict'
  tableId: number
  recordId: number
  fieldId: number
  message: string
}

export interface CursorMoveEvent {
  type: 'cursor_moved'
  userId: number
  userName: string
  tableId: number
  recordId: number
  fieldId: number
}

const WS_BASE = (import.meta.env.VITE_WS_URL || 'ws://localhost:8081').replace(/\/$/, '')

export function useBitableWebSocket(baseId: number) {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const cursors = ref<Map<string, CollaborationCursor>>(new Map())
  const onlineUsers = ref<OnlineCollaborator[]>([])
  const reconnectAttempts = ref(0)

  const onCellUpdated = ref<((event: CellUpdateEvent) => void) | null>(null)
  const onConflict = ref<((event: ConflictEvent) => void) | null>(null)

  function connect() {
    if (ws.value?.readyState === WebSocket.OPEN || ws.value?.readyState === WebSocket.CONNECTING) return

    const token = getToken()
    if (!token) {
      onlineUsers.value = []
      return
    }

    const url = `${WS_BASE}/ws/bitable/${baseId}?accessToken=${encodeURIComponent(token)}`
    ws.value = new WebSocket(url)

    ws.value.onopen = () => {
      connected.value = true
      reconnectAttempts.value = 0
    }

    ws.value.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data) as
          | CellUpdateEvent
          | ConflictEvent
          | CursorMoveEvent
          | PresenceUpdatedEvent

        if (msg.type === 'cell_updated' && onCellUpdated.value) {
          onCellUpdated.value(msg)
        } else if (msg.type === 'conflict' && onConflict.value) {
          onConflict.value(msg)
        } else if (msg.type === 'cursor_moved') {
          const key = `${msg.tableId}_${msg.recordId}_${msg.fieldId}`
          cursors.value.set(key, { ...msg, timestamp: Date.now() })
        } else if (msg.type === 'presence_updated') {
          onlineUsers.value = Array.isArray(msg.users) ? msg.users : []
        }
      } catch {
        // ignore parse errors
      }
    }

    ws.value.onclose = () => {
      connected.value = false
      onlineUsers.value = []
      ws.value = null
      if (reconnectAttempts.value < 5) {
        reconnectAttempts.value++
        setTimeout(connect, 3000)
      }
    }
  }

  function disconnect() {
    reconnectAttempts.value = 99 // prevent reconnect
    ws.value?.close()
    ws.value = null
    connected.value = false
    onlineUsers.value = []
  }

  function sendCellUpdate(
    tableId: number,
    recordId: number,
    fieldId: number,
    value: unknown,
    version: number
  ) {
    if (!ws.value || ws.value.readyState !== WebSocket.OPEN) return
    ws.value.send(
      JSON.stringify({
        type: 'cell_update',
        tableId,
        recordId,
        fieldId,
        value,
        version,
      })
    )
  }

  function sendCursorMove(tableId: number, recordId: number, fieldId: number) {
    if (!ws.value || ws.value.readyState !== WebSocket.OPEN) return
    ws.value.send(
      JSON.stringify({
        type: 'cursor_move',
        tableId,
        recordId,
        fieldId,
      })
    )
  }

  // 清理过期光标(超过30秒)
  const cursorCleanup = setInterval(() => {
    const now = Date.now()
    cursors.value.forEach((cursor, key) => {
      if (now - cursor.timestamp > 30000) {
        cursors.value.delete(key)
      }
    })
  }, 10000)

  onUnmounted(() => {
    clearInterval(cursorCleanup)
    disconnect()
  })

  return {
    ws,
    connected,
    cursors,
    onlineUsers,
    reconnectAttempts,
    onCellUpdated,
    onConflict,
    connect,
    disconnect,
    sendCellUpdate,
    sendCursorMove,
  }
}
