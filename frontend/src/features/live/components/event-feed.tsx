import { useEffect, useRef } from 'react'

import {
  BidIcon,
  ClosingIcon,
  SoftCloseIcon,
  StartIcon,
  WinIcon,
} from '@/features/live/components/event-icons'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

/**
 * 이벤트 한 줄의 아이콘 칩과 글자색을 Figma `ev*` 값 그대로 고른다.
 *
 * 같은 `CLOSE` 라도 낙찰 확정(초록 체크)과 마감 임박(빨강 시계)이 다르고,
 * 같은 `BID` 라도 최신 입찰만 진하게 쓴다.
 */
function resolveStyle(event: RoomEvent) {
  switch (event.kind) {
    case 'START':
      return {
        chip: 'bg-fill',
        icon: <StartIcon />,
        text: 'text-[14px] font-semibold text-foreground',
      }
    case 'CLOSE':
      return event.subtitle
        ? {
            chip: 'bg-success-surface',
            icon: <WinIcon />,
            text: 'text-[14px] font-bold text-foreground',
          }
        : {
            chip: 'bg-live-surface',
            icon: <ClosingIcon />,
            text: 'text-[14px] font-semibold text-foreground',
          }
    case 'EXTEND':
      return {
        chip: 'bg-notice-surface',
        icon: <SoftCloseIcon />,
        text: 'text-[14px] font-bold text-notice',
      }
    case 'REJECT':
      return {
        chip: 'bg-live-surface',
        icon: <ClosingIcon />,
        text: 'text-[14px] font-semibold text-live',
      }
    case 'BID':
    default:
      return {
        chip: 'bg-brand-100',
        icon: <BidIcon />,
        text: event.emphasized
          ? 'text-[14px] font-bold text-foreground'
          : 'text-[14px] font-medium text-neutral-tertiary',
      }
  }
}

/**
 * 경매방 실시간 이벤트 피드.
 *
 * **오래된 이벤트가 위, 최신이 맨 아래**다. 새 이벤트가 들어오면 아래로
 * 쌓이고 자동으로 맨 아래로 따라간다. 카드 안에서만 스크롤한다.
 */
/** 시간 오름차순. 늦은 이벤트가 아래로 간다. */
function byTimeAsc(events: RoomEvent[]) {
  return [...events].sort(
    (a, b) => new Date(a.at).getTime() - new Date(b.at).getTime(),
  )
}

export function EventFeed({ events }: { events: RoomEvent[] }) {
  const ordered = byTimeAsc(events)
  const scrollRef = useRef<HTMLDivElement>(null)

  // 새 이벤트가 오면 맨 아래로 붙인다.
  useEffect(() => {
    const node = scrollRef.current
    if (node) node.scrollTop = node.scrollHeight
  }, [events])

  return (
    <div
      ref={scrollRef}
      className="h-[420px] overflow-y-auto rounded-[20px] border bg-card lg:h-auto lg:min-h-0 lg:flex-1"
    >
      {events.length === 0 ? (
        <div className="flex h-full flex-col items-center justify-center px-6 text-center">
          <p className="text-[15px] font-bold text-neutral-secondary">
            아직 이벤트가 없어요
          </p>
          <p className="mt-2 text-[13px] font-medium text-neutral-muted">
            입찰이 들어오면 여기에 실시간으로 표시됩니다.
          </p>
        </div>
      ) : (
        // 아래에서부터 쌓이도록 위쪽 여백을 자동으로 밀어낸다.
        <ul className="flex min-h-full flex-col justify-end gap-[30px] p-5">
          {ordered.map((event, index) => {
            const style = resolveStyle(event)
            const latest = event.id === ordered[ordered.length - 1]?.id

            return (
              <li
                key={event.id}
                className="animate-rise flex items-start gap-3"
                style={{ animationDelay: `${index * 40}ms` }}
              >
                <span
                  className={cn(
                    'flex size-9 shrink-0 items-center justify-center rounded-[10px]',
                    style.chip,
                  )}
                >
                  {style.icon}
                </span>

                <div className="min-w-0 flex-1 pt-0.5">
                  <p className={style.text}>{event.message}</p>
                  {event.subtitle && (
                    <p
                      className={cn(
                        'mt-1 text-[12px]',
                        event.kind === 'CLOSE'
                          ? 'font-semibold text-success'
                          : 'font-normal text-neutral-tertiary',
                      )}
                    >
                      {event.subtitle}
                    </p>
                  )}
                </div>

                <time
                  className={cn(
                    'shrink-0 pt-0.5 text-[12px] tabular-nums text-neutral-muted',
                    latest ? 'font-semibold' : 'font-normal',
                  )}
                >
                  {formatTime(event.at)}
                </time>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
