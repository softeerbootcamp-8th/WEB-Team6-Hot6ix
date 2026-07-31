import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * 상태 배지.
 *
 * Figma `17 배지 전략` 기준. 색만으로 상태를 구분하지 않도록 항상 라벨을
 * 함께 쓰고, LIVE 처럼 주의를 끌어야 하는 상태에만 강한 색을 쓴다.
 */
export type BadgeTone =
  'live' | 'success' | 'notice' | 'brand' | 'neutral' | 'muted'

const TONE_CLASS: Record<BadgeTone, string> = {
  live: 'bg-live-surface text-live',
  success: 'bg-success-surface text-success',
  notice: 'bg-notice-surface text-notice',
  brand: 'bg-brand-50 text-brand-600',
  neutral: 'bg-fill text-neutral-secondary',
  muted: 'bg-fill text-neutral-muted',
}

export function StatusBadge({
  tone = 'neutral',
  children,
  dot = false,
  className,
}: {
  tone?: BadgeTone
  children: ReactNode
  /** LIVE 처럼 진행 중임을 표시할 때 앞에 점을 찍는다. */
  dot?: boolean
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-sm px-2 py-1 text-badge font-extrabold',
        TONE_CLASS[tone],
        className,
      )}
    >
      {dot && <span aria-hidden className="size-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}
