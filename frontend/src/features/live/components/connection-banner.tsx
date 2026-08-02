import { Loader2, WifiOff } from 'lucide-react'

import { StatusBadge } from '@/components/status-badge'
import type { RealtimeStatus } from '@/features/live/use-realtime-status'

/** 상단바 옆에 붙는 연결 상태 표시. 색만이 아니라 문구로도 구분한다. */
export function ConnectionPill({ status }: { status: RealtimeStatus }) {
  if (status === 'connected') {
    return (
      <StatusBadge tone="success" dot>
        실시간 연결됨
      </StatusBadge>
    )
  }

  if (status === 'failed') {
    return <StatusBadge tone="live">연결 끊김</StatusBadge>
  }

  return (
    <StatusBadge tone="notice">
      {status === 'connecting' ? '연결 중…' : '재연결 중…'}
    </StatusBadge>
  )
}

/**
 * 연결이 끊겼을 때 본문 위에 뜨는 배너.
 *
 * 끊긴 동안 보이는 값은 최신이 아닐 수 있다는 걸 분명히 알린다.
 */
export function ConnectionBanner({
  status,
  onRetry,
}: {
  status: RealtimeStatus
  onRetry: () => void
}) {
  if (status === 'connected' || status === 'connecting') return null

  const reconnecting = status === 'reconnecting'

  return (
    <div
      role="status"
      className="flex flex-wrap items-center gap-3 rounded-2xl border border-notice/30 bg-notice-surface px-4 py-3"
    >
      {reconnecting ? (
        <Loader2 aria-hidden className="size-4 animate-spin text-notice" />
      ) : (
        <WifiOff aria-hidden className="size-4 text-live" />
      )}
      <p className="text-label font-bold text-notice">
        {reconnecting
          ? '실시간 연결을 다시 맺는 중이에요. 표시된 금액이 최신이 아닐 수 있어요.'
          : '실시간 연결이 끊겼어요. 표시된 금액이 최신이 아닐 수 있어요.'}
      </p>
      <button
        type="button"
        onClick={onRetry}
        disabled={reconnecting}
        className="ml-auto rounded-lg border border-notice/40 px-3 py-1.5 text-caption font-bold text-notice transition-colors hover:bg-notice/10 disabled:opacity-50"
      >
        다시 연결
      </button>
    </div>
  )
}
