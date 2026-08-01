import {
  BidIcon,
  ClosingIcon,
  SoftCloseIcon,
  StartIcon,
  WinIcon,
} from '@/features/live/components/event-icons'
import { useEffect, useRef } from 'react'

import { useEventEntrance } from '@/features/live/use-event-entrance'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

/**
 * 물품 상세의 `이 물품의 경매방 이벤트` 카드 (Figma 476×260).
 *
 * 방 전체 피드와 달리 카드 안에 담기고, 행 간격이 66px 로 넓다.
 * 최신 이벤트가 위에 온다.
 */
function resolveStyle(event: RoomEvent) {
  switch (event.kind) {
    case 'START':
      return {
        chip: 'bg-fill',
        icon: <StartIcon />,
        text: 'font-semibold text-foreground',
      }
    case 'CLOSE':
      return event.subtitle
        ? {
            chip: 'bg-success-surface',
            icon: <WinIcon />,
            text: 'font-bold text-foreground',
          }
        : {
            chip: 'bg-live-surface',
            icon: <ClosingIcon />,
            text: 'font-semibold text-foreground',
          }
    case 'EXTEND':
      return {
        chip: 'bg-notice-surface',
        icon: <SoftCloseIcon />,
        text: 'font-bold text-notice',
      }
    case 'REJECT':
      return {
        chip: 'bg-live-surface',
        icon: <ClosingIcon />,
        text: 'font-semibold text-live',
      }
    case 'BID':
    default:
      return {
        chip: 'bg-brand-100',
        icon: <BidIcon />,
        text: 'font-semibold text-foreground',
      }
  }
}

/** 시간 오름차순. 늦은 이벤트가 아래로 간다. */
function byTimeAsc(events: RoomEvent[]) {
  return [...events].sort(
    (a, b) => new Date(a.at).getTime() - new Date(b.at).getTime(),
  )
}

export function ItemEventList({ events }: { events: RoomEvent[] }) {
  const entranceOf = useEventEntrance()
  const scrollRef = useRef<HTMLDivElement>(null)

  // 새 이벤트가 오면 맨 아래로 부드럽게 따라간다.
  useEffect(() => {
    const node = scrollRef.current
    if (!node) return
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
  }, [events])

  return (
    <section className="flex min-h-0 flex-col rounded-2xl border p-5 lg:flex-1">
      <h3 className="shrink-0 text-[13px] font-bold text-neutral-tertiary">
        이 물품의 경매방 이벤트
      </h3>

      {events.length === 0 ? (
        // 남는 높이를 채워 박스 한가운데 놓는다. 위에만 붙으면 빈칸이 커 보인다.
        <div className="flex min-h-0 flex-1 items-center justify-center">
          <p className="text-center text-[12px] font-medium text-neutral-muted">
            아직 이벤트가 없어요.
          </p>
        </div>
      ) : (
        <div
          ref={scrollRef}
          className="mt-3 min-h-0 flex-1 overflow-y-auto border-t pt-2"
        >
          {/* 아래에서부터 쌓이도록 위쪽 여백을 자동으로 밀어낸다 */}
          <ul className="flex min-h-full flex-col justify-end gap-4">
            {byTimeAsc(events).map((event, index) => {
              const style = resolveStyle(event)
              return (
                <li
                  key={event.id}
                  className={cn(
                    /*
                     * 음수 마진을 쓰면 스크롤 컨테이너가 좌우로 삐져나온
                     * 부분을 잘라내서, 둥근 모서리가 깎여 직사각형으로 보인다.
                     * 안쪽 여백만으로 배경을 만든다.
                     */
                    'flex items-start gap-3 rounded-2xl px-3 py-2',
                    entranceOf(event.id) === 'incoming'
                      ? 'animate-event'
                      : 'animate-rise',
                  )}
                  style={
                    entranceOf(event.id) === 'incoming'
                      ? undefined
                      : { animationDelay: `${index * 40}ms` }
                  }
                >
                  <span
                    className={cn(
                      'flex size-9 shrink-0 items-center justify-center rounded-[10px]',
                      style.chip,
                      entranceOf(event.id) === 'incoming' &&
                        'animate-event-chip',
                    )}
                  >
                    {style.icon}
                  </span>

                  {/* 한 물품만 보는 화면이라 물품명은 다시 적지 않는다. */}
                  <div className="min-w-0 flex-1">
                    <p className={cn('text-[13px]', style.text)}>
                      {event.message}
                    </p>
                  </div>

                  <time className="shrink-0 text-[11px] font-normal tabular-nums text-neutral-muted">
                    {formatTime(event.at)}
                  </time>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </section>
  )
}
