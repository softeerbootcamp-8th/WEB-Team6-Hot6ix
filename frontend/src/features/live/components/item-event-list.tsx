import {
  BidIcon,
  ClosingIcon,
  SoftCloseIcon,
  StartIcon,
  WinIcon,
} from '@/features/live/components/event-icons'
import { useEffect, useRef, useState } from 'react'

import { useEventEntrance } from '@/features/live/use-event-entrance'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

/**
 * 물품 상세의 `이 물품의 경매방 이벤트` 카드 (Figma 476×260).
 *
 * 방 전체 피드와 달리 카드 안에 담기고, 행 간격이 66px 로 넓다.
 * 이벤트는 시간 오름차순으로 정렬되어 최신 이벤트가 아래에 쌓인다.
 */
function resolveStyle(event: RoomEvent) {
  switch (event.kind) {
    case 'START':
      return {
        chip: 'bg-fill',
        icon: <StartIcon />,
        text: 'font-semibold text-foreground',
      }
    case 'WIN':
      return {
        chip: 'bg-success-surface',
        icon: <WinIcon />,
        text: 'font-semibold text-foreground',
      }
    case 'CLOSE':
      return {
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

const BOTTOM_THRESHOLD = 10 // px

export function ItemEventList({ events }: { events: RoomEvent[] }) {
  const entranceOf = useEventEntrance()
  const scrollRef = useRef<HTMLDivElement>(null)
  const isAtBottomRef = useRef(true)
  const [hasNewEvents, setHasNewEvents] = useState(false)

  // 사용자가 스크롤할 때 맨 아래 여부를 추적한다.
  function handleScroll() {
    const node = scrollRef.current
    if (!node) return
    const atBottom =
      node.scrollTop + node.clientHeight >= node.scrollHeight - BOTTOM_THRESHOLD
    isAtBottomRef.current = atBottom
    if (atBottom) setHasNewEvents(false)
  }

  function scrollToBottom() {
    const node = scrollRef.current
    if (!node) return
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
    setHasNewEvents(false)
  }

  // 새 이벤트가 오면(length 증가): 맨 아래를 보고 있을 때만 따라가고,
  // 위로 올려둔 상태면 위치를 유지하고 배지를 표시한다.
  // events 참조가 바뀌어도 length 가 같으면 실행하지 않는다.
  useEffect(() => {
    if (isAtBottomRef.current) {
      scrollToBottom()
    } else {
      setHasNewEvents(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [events.length])

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
        <div className="relative mt-3 min-h-0 flex-1">
          <div
            ref={scrollRef}
            onScroll={handleScroll}
            className="h-full overflow-y-auto border-t pt-2"
          >
            {/* 아래에서부터 쌓이도록 위쪽 여백을 자동으로 밀어낸다 */}
            {/* 새로 들어온 줄만 읽어준다 (`EventFeed` 와 같은 규칙) */}
            <ul
              aria-live="polite"
              aria-relevant="additions"
              aria-label="이 물품의 경매방 이벤트"
              className="flex min-h-full flex-col justify-end gap-4"
            >
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
                      // 실시간으로 들어온 줄은 연출 없이 바로 읽힌다 (event-feed 참고)
                      entranceOf(event.id) === 'initial' && 'animate-rise',
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
          {hasNewEvents && (
            <button
              onClick={scrollToBottom}
              className="absolute bottom-2 left-1/2 -translate-x-1/2 flex items-center gap-1 rounded-full bg-foreground px-3 py-1 text-[11px] font-semibold text-background shadow-md"
            >
              새 이벤트 ↓
            </button>
          )}
        </div>
      )}
    </section>
  )
}
