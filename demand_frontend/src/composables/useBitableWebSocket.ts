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

export interface RecordCreatedEvent {
  type: 'record_created'
  tableId: number
  recordId: number
  userId: number
  userName: string
}

export interface RecordDeletedEvent {
  type: 'record_deleted'
  tableId: number
  recordId: number
  userId: number
  userName: string
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
  const onRecordCreated = ref<((event: RecordCreatedEvent) => void) | null>(null)
  const onRecordDeleted = ref<((event: RecordDeletedEvent) => void) | null>(null)

  function connect() {
    if (ws.value?.readyState === WebSocket.OPEN || ws.value?.readyState === WebSocket.CONNECTING) return

    const token = getToken()
    if (!token) {
      onlineUsers.value = []
      return
    }

    // token 经 Sec-WebSocket-Protocol 子协议头传输（bearer,<token>），
    // 避免明文出现在 URL 中被访问日志记录；服务端握手后回显子协议。
    const url = `${WS_BASE}/ws/bitable/${baseId}`
    ws.value = new WebSocket(url, ['bearer', token])

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
          | RecordCreatedEvent
          | RecordDeletedEvent

        if (msg.type === 'cell_updated' && onCellUpdated.value) {
          onCellUpdated.value(msg)
        } else if (msg.type === 'conflict' && onConflict.value) {
          onConflict.value(msg)
        } else if (msg.type === 'record_created' && onRecordCreated.value) {
          onRecordCreated.value(msg)
        } else if (msg.type === 'record_deleted' && onRecordDeleted.value) {
          onRecordDeleted.value(msg)
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

  // 单元格写入已统一走 REST（updateCell 成功后由后端广播 cell_updated），
  // WebSocket 不再提供 cell_update 上行，服务端也会拒绝该消息类型。

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
    onRecordCreated,
    onRecordDeleted,
    connect,
    disconnect,
    sendCursorMove,
  }
}
