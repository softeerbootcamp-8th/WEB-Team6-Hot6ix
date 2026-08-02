import { Timer } from 'lucide-react'

import { formatRemaining } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'

/**
 * 남은 시간 표시.
 *
 * 0 이 되어도 "마감"이라고 단정하지 않고 "마감 처리 중"으로 표시한다.
 * 마감 확정은 서버 이벤트로만 이뤄진다.
 */
export function Countdown({
  endsAt,
  closed = false,
  className,
}: {
  endsAt: string
  /** 서버가 마감을 확정한 물품 */
  closed?: boolean
  className?: string
}) {
  const remaining = useCountdown(endsAt)
  const closingSoon = isClosingSoon(remaining)

  if (closed) {
    return (
      <span
        className={cn(
          'inline-flex items-center gap-1.5 text-timer font-semibold text-neutral-muted',
          className,
        )}
      >
        <Timer aria-hidden className="size-3.5" />
        마감됨
      </span>
    )
  }

  if (remaining === 0) {
    return (
      <span
        aria-live="polite"
        className={cn(
          'inline-flex items-center gap-1.5 text-timer font-semibold text-notice',
          className,
        )}
      >
        <Timer aria-hidden className="size-3.5" />
        마감 처리 중
      </span>
    )
  }

  return (
    <span
      aria-live={closingSoon ? 'polite' : 'off'}
      className={cn(
        'inline-flex items-center gap-1.5 text-timer font-semibold tabular-nums',
        closingSoon ? 'text-live' : 'text-neutral-secondary',
        className,
      )}
    >
      <Timer aria-hidden className="size-3.5" />
      {closingSoon && <span className="sr-only">마감 임박</span>}
      {formatRemaining(remaining)}
      {closingSoon && <span aria-hidden>🔥</span>}
    </span>
  )
}
