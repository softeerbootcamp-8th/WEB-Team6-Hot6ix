import { useCallback, useEffect, useRef, useState } from 'react'

export type RealtimeStatus =
  'connecting' | 'connected' | 'reconnecting' | 'failed'

/**
 * 실시간 연결 상태.
 *
 * SSE/WebSocket 선택이 아직 확정되지 않아서 지금은 상태 기계만 만들어 둔다.
 * 실제 구독이 붙어도 화면이 보는 값은 이 상태 그대로다.
 *
 * 구독 시작·종료는 한 곳에서만 하고, 언마운트 시 타이머를 모두 정리한다.
 */
export function useRealtimeStatus() {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const timers = useRef<number[]>([])

  const clearTimers = useCallback(() => {
    timers.current.forEach((id) => window.clearTimeout(id))
    timers.current = []
  }, [])

  useEffect(() => {
    const id = window.setTimeout(() => setStatus('connected'), 600)
    timers.current.push(id)
    return clearTimers
  }, [clearTimers])

  /** 연결이 끊긴 상황을 재현한다. 재연결 화면을 확인할 때 쓴다. */
  const simulateDrop = useCallback(() => {
    clearTimers()
    setStatus('reconnecting')
    const id = window.setTimeout(() => setStatus('connected'), 3000)
    timers.current.push(id)
  }, [clearTimers])

  const retry = useCallback(() => {
    clearTimers()
    setStatus('reconnecting')
    const id = window.setTimeout(() => setStatus('connected'), 1200)
    timers.current.push(id)
  }, [clearTimers])

  return { status, simulateDrop, retry }
}
