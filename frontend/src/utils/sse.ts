import { API_BASE_URL } from '@/config/env'

/**
 * SSE helper functions
 */
export interface SSEMessage {
  type: string
  data?: any
  [key: string]: any
}

export interface SSEOptions {
  onMessage: (message: SSEMessage) => void
  onError?: (error: Event) => void
  onComplete?: () => void
}

/**
 * Create SSE connection
 */
export const connectSSE = (taskId: string, options: SSEOptions): EventSource => {
  const { onMessage, onError, onComplete } = options

  const eventSource = new EventSource(`${API_BASE_URL}/article/progress/${taskId}`, {
    withCredentials: true,
  })

  eventSource.onmessage = (event) => {
    try {
      const message: SSEMessage = JSON.parse(event.data)
      onMessage(message)

      if (message.type === 'ALL_COMPLETE' || message.type === 'ERROR') {
        eventSource.close()
        onComplete?.()
      }
    } catch (error) {
      console.error('SSE message parse failed:', error)
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error)
    onError?.(error)
  }

  return eventSource
}

/**
 * Close SSE connection
 */
export const closeSSE = (eventSource: EventSource | null) => {
  if (eventSource) {
    eventSource.close()
  }
}
