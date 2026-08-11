import { useEffect, useRef } from 'react'

import { useEventEntrance } from '@/features/live/use-event-entrance'

import { EventItemTag } from '@/features/live/components/event-item-tag'
import { resolveEventTone } from '@/features/live/components/event-tone'
import { formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

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

  const entranceOf = useEventEntrance()

  // 새 이벤트가 오면 맨 아래로 부드럽게 따라간다.
  useEffect(() => {
    const node = scrollRef.current
    if (!node) return
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
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
  )
}
