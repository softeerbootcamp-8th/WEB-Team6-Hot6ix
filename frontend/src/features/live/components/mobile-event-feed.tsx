import { useEffect, useRef } from 'react'

import {
  BidIcon,
  ClosingIcon,
  SoftCloseIcon,
  StartIcon,
  WinIcon,
} from '@/features/live/components/event-icons'
import { useEventEntrance } from '@/features/live/use-event-entrance'
import { cn } from '@/lib/utils'
import { formatTime } from '@/lib/format'
import type { RoomEvent } from '@/mocks/types'

/**
 * 이벤트 한 줄의 배경·칩 색. Figma `MOB-04` 값을 그대로 쓴다.
 * 웹 피드(`event-feed.tsx`)는 칩만 칠하는데 모바일은 줄 전체가 칠해진다.
 */
function resolveTone(event: RoomEvent) {
  switch (event.kind) {
    case 'START':
      return { row: 'bg-[#f7f9fb]', chip: 'bg-fill', icon: <StartIcon /> }
    case 'CLOSE':
      return event.subtitle
        ? { row: 'bg-[#f2fcf8]', chip: 'bg-[#e7f7ef]', icon: <WinIcon /> }
        : { row: 'bg-[#fff5f5]', chip: 'bg-[#fdeced]', icon: <ClosingIcon /> }
    case 'EXTEND':
      return {
        row: 'bg-notice-surface/50',
        chip: 'bg-notice-surface',
        icon: <SoftCloseIcon />,
      }
    case 'REJECT':
      return {
        row: 'bg-[#fff5f5]',
        chip: 'bg-[#fdeced]',
        icon: <ClosingIcon />,
      }
    case 'BID':
    default:
      return { row: 'bg-[#f4f9ff]', chip: 'bg-brand-100', icon: <BidIcon /> }
  }
}

/** 시간 오름차순 — 늦은 이벤트가 아래로 간다 (웹 피드와 같은 규칙). */
export function MobileEventFeed({ events }: { events: RoomEvent[] }) {
  const ordered = [...events].sort(
    (a, b) => new Date(a.at).getTime() - new Date(b.at).getTime(),
  )
  const scrollRef = useRef<HTMLDivElement>(null)
  const entranceOf = useEventEntrance()

  // 새 이벤트가 오면 맨 아래로 부드럽게 따라간다.
  useEffect(() => {
    const node = scrollRef.current
    if (!node) return
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
  }, [events])

  return (
    <div className="flex h-full flex-col p-4">
      {ordered.length === 0 ? (
        <p className="rounded-xl bg-surface-subtle px-4 py-8 text-center text-[13px] font-medium text-neutral-muted">
          아직 이벤트가 없어요.
        </p>
      ) : (
        <div
          ref={scrollRef}
          className="min-h-0 flex-1 overflow-y-auto overscroll-contain pr-1"
        >
          {/* 아래에서부터 쌓이도록 위쪽 여백을 자동으로 밀어낸다 */}
          <ul className="flex min-h-full flex-col justify-end gap-2">
            {ordered.map((event, index) => {
              const tone = resolveTone(event)

              return (
                <li
                  key={event.id}
                  style={
                    entranceOf(event.id) === 'incoming'
                      ? undefined
                      : { animationDelay: `${index * 40}ms` }
                  }
                  className={cn(
                    'flex min-h-[52px] items-center gap-3 rounded-xl px-2',
                    tone.row,
                    entranceOf(event.id) === 'incoming'
                      ? 'animate-event'
                      : 'animate-rise',
                  )}
                >
                  <span
                    className={cn(
                      'flex size-9 shrink-0 items-center justify-center rounded-[10px]',
                      tone.chip,
                      entranceOf(event.id) === 'incoming' &&
                        'animate-event-chip',
                    )}
                  >
                    {tone.icon}
                  </span>

                  <div className="min-w-0 flex-1 py-2">
                    <p className="text-[13px] font-semibold text-foreground">
                      {event.message}
                    </p>
                    <time className="mt-1 block text-[11px] font-medium tabular-nums text-neutral-muted">
                      {formatTime(event.at)}
                    </time>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}
