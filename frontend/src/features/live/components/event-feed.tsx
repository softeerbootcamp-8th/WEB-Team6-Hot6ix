import { useEffect, useRef, useState } from 'react'

import { useEventEntrance } from '@/features/live/use-event-entrance'

import { EventItemTag } from '@/features/live/components/event-item-tag'
import { resolveEventTone } from '@/features/live/components/event-tone'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

/**
 * 경매방 실시간 이벤트 피드.
 *
 * **오래된 이벤트가 위, 최신이 맨 아래**다. 새 이벤트가 들어오면 아래로 쌓인다.
 * 카드 안에서만 스크롤한다.
 *
 * 맨 아래를 보고 있을 때만 새 이벤트를 따라간다. 예전에는 이벤트가 올 때마다
 * 무조건 맨 아래로 내렸는데, 입찰이 초당 여러 건 들어오는 방에서는 지난
 * 이벤트를 올려 읽는 도중에 계속 끌려 내려가 읽을 수가 없었다. 위로 올려둔
 * 상태에서는 자리를 지키고 `새 이벤트 ↓` 만 띄운다
 * (`ItemEventList`·`MobileEventFeed` 와 같은 규칙).
 */
/** 시간 오름차순. 늦은 이벤트가 아래로 간다. */
function byTimeAsc(events: RoomEvent[]) {
  return [...events].sort(
    (a, b) => new Date(a.at).getTime() - new Date(b.at).getTime(),
  )
}

const BOTTOM_THRESHOLD = 10 // px

export function EventFeed({ events }: { events: RoomEvent[] }) {
  const ordered = byTimeAsc(events)
  const scrollRef = useRef<HTMLDivElement>(null)
  const isAtBottomRef = useRef(true)
  const [hasNewEvents, setHasNewEvents] = useState(false)

  const entranceOf = useEventEntrance()

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

  // 새 이벤트가 오면(length 증가) 맨 아래를 보고 있을 때만 따라간다.
  // events 참조가 바뀌어도 length 가 같으면 실행하지 않는다.
  useEffect(() => {
    if (isAtBottomRef.current) scrollToBottom()
    else setHasNewEvents(true)
  }, [events.length])

  return (
    // 스크롤은 안쪽 칸이 맡는다. 바깥은 `새 이벤트 ↓` 를 띄울 기준면이다.
    <div className="relative flex h-[420px] flex-col overflow-hidden rounded-[20px] border bg-card lg:h-auto lg:min-h-0 lg:flex-1">
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="min-h-0 flex-1 overflow-y-auto"
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
          <ul className="flex min-h-full flex-col justify-end gap-2 p-2">
            {ordered.map((event, index) => {
              const tone = resolveEventTone(event)

              return (
                <li
                  key={event.id}
                  className={cn(
                    /*
                     * 음수 마진을 쓰면 스크롤 컨테이너가 좌우로 삐져나온 부분을
                     * 잘라내 둥근 모서리가 깎인다. 안쪽 여백만으로 배경을 만든다.
                     */
                    'flex items-center gap-3 rounded-2xl px-3 py-2',
                    // 줄 배경도 모바일과 같은 색을 쓴다.
                    tone.row,
                    /*
                     * 실시간으로 들어온 줄에는 등장 연출을 걸지 않는다. 덮개가
                     * 글자 위까지 덮어서 박스만 먼저 뜨고 문구가 1.5초 뒤에
                     * 나타났다. 알림은 도착 즉시 읽히는 쪽이 맞다.
                     */
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
                      tone.chip,
                    )}
                  >
                    {tone.icon}
                  </span>

                  <div className="min-w-0 flex-1">
                    {/* 어느 물품인지는 종류와 상관없이 이름표가 맡는다. */}
                    <EventItemTag event={event} />
                    {/*
                    이름표는 알약이라 안쪽 여백(px-1.5)만큼 글자가 들어가 있다.
                    아래 문구도 같은 만큼 밀어야 글자 시작선이 나란해진다.
                  */}
                    <div className="pl-1.5">
                      <p className="text-[13px] font-semibold text-foreground">
                        {event.message}
                      </p>
                    </div>
                  </div>

                  <time className="shrink-0 text-[11px] font-medium tabular-nums text-neutral-muted">
                    {formatTime(event.at)}
                  </time>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      {hasNewEvents && (
        <button
          type="button"
          onClick={scrollToBottom}
          className="absolute bottom-2 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full bg-foreground px-3 py-1 text-[11px] font-semibold text-background shadow-md"
        >
          새 이벤트 ↓
        </button>
      )}
    </div>
  )
}
