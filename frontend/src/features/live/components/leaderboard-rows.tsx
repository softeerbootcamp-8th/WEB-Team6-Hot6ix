import { useEffect, useLayoutEffect, useRef, useState } from 'react'

import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import type { LeaderboardEntry } from '@/mocks/types'

/** 방금 무슨 일이 있었는지. 배지로 잠깐 보여준다. */
type Movement = { kind: 'up' | 'down' | 'new'; delta: number }

/** 변동 배지가 남아 있는 시간. 너무 짧으면 못 보고 놓친다. */
const MOVEMENT_MS = 2600

/**
 * 리더보드 순위 행.
 *
 * 라이브 오른쪽 열의 물품별 리더보드와 물품 상세의 실시간 리더보드가
 * 같은 모양을 쓰도록 한 곳에 둔다. 상위 3명만 보여준다.
 *
 * 순위가 바뀌면 세 가지가 한꺼번에 일어난다.
 * 1. 행이 이전 위치에서 새 위치로 미끄러진다 (FLIP)
 * 2. 올라온 사람은 튕기며 잠깐 밝아지고, 밀려난 사람은 한 박자 늦게 내려앉는다
 * 3. `▲2` `▼1` `NEW` 배지가 몇 초간 붙는다
 *
 * 위치를 **닉네임**으로 기억하는 게 핵심이다. 등수로 기억하면 "1등 칸"의
 * 좌표는 늘 같아서 움직일 거리가 0 이 되고, 결국 글자만 바뀐다.
 */
export function LeaderboardRows({
  entries,
  emptyText = '아직 입찰이 없어요',
}: {
  entries: LeaderboardEntry[]
  emptyText?: string
}) {
  const top3 = entries.slice(0, 3)
  const signature = top3
    .map((entry) => `${entry.nickname}:${entry.rank}`)
    .join('|')

  const rows = useRef(new Map<string, HTMLLIElement>())
  const positions = useRef(new Map<string, number>())
  const ranks = useRef(new Map<string, number>())
  const mounted = useRef(false)
  const timer = useRef<number | null>(null)
  const [movements, setMovements] = useState<Map<string, Movement>>(new Map())

  useLayoutEffect(() => {
    const reduced = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    // 처음 그릴 때는 자리만 기억한다. 화면을 열자마자 다 튀면 산만하다.
    const first = !mounted.current
    mounted.current = true

    const changed = new Map<string, Movement>()
    if (!first) {
      top3.forEach((entry) => {
        const before = ranks.current.get(entry.nickname)
        if (before === undefined) {
          changed.set(entry.nickname, { kind: 'new', delta: 0 })
        } else if (before > entry.rank) {
          changed.set(entry.nickname, {
            kind: 'up',
            delta: before - entry.rank,
          })
        } else if (before < entry.rank) {
          changed.set(entry.nickname, {
            kind: 'down',
            delta: entry.rank - before,
          })
        }
      })
    }
    ranks.current = new Map(top3.map((entry) => [entry.nickname, entry.rank]))

    rows.current.forEach((element, nickname) => {
      const next = element.getBoundingClientRect().top
      const previous = positions.current.get(nickname)
      positions.current.set(nickname, next)
      if (reduced || first) return

      if (previous === undefined) {
        // 새로 치고 들어온 사람 — 목록 아래에서 끼어든다.
        element.animate(
          [
            { opacity: 0, transform: 'translateY(34px) scale(0.9)' },
            {
              opacity: 1,
              transform: 'translateY(-6px) scale(1.04)',
              offset: 0.62,
            },
            { opacity: 1, transform: 'none' },
          ],
          { duration: 620, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
        )
        flash(element)
        return
      }

      const delta = previous - next
      if (delta === 0) return

      if (delta > 0) {
        /*
         * 올라온 사람. 중간에 살짝 커졌다가 목표 지점을 지나쳐 튕긴다.
         * 등속으로 자리만 바꾸면 "누가 치고 올라왔는지"가 남지 않는다.
         */
        element.animate(
          [
            { transform: `translateY(${delta}px) scale(1)`, offset: 0 },
            {
              transform: `translateY(${delta * 0.22}px) scale(1.05)`,
              offset: 0.45,
            },
            { transform: 'translateY(-5px) scale(1.02)', offset: 0.78 },
            { transform: 'none' },
          ],
          { duration: 640, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
        )
        flash(element)
      } else {
        // 밀려난 사람은 한 박자 늦게, 잠깐 흐려지며 내려앉는다.
        element.animate(
          [
            { transform: `translateY(${delta}px)`, opacity: 1 },
            { transform: `translateY(${delta * 0.2}px)`, opacity: 0.7 },
            { transform: 'none', opacity: 1 },
          ],
          {
            duration: 520,
            delay: 90,
            easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
            fill: 'backwards',
          },
        )
      }
    })

    // 순위 밖으로 밀려난 사람은 기억에서 지운다.
    positions.current.forEach((_, nickname) => {
      if (!rows.current.has(nickname)) positions.current.delete(nickname)
    })

    if (changed.size > 0) {
      setMovements(changed)
      if (timer.current !== null) window.clearTimeout(timer.current)
      timer.current = window.setTimeout(
        () => setMovements(new Map()),
        MOVEMENT_MS,
      )
    }
    // 순위 구성이 바뀔 때만 다시 계산한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature])

  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    },
    [],
  )

  if (top3.length === 0) {
    return (
      <p className="rounded-lg bg-surface-subtle py-5 text-center text-[12px] font-medium text-neutral-muted">
        {emptyText}
      </p>
    )
  }

  return (
    <ol className="space-y-1">
      {top3.map((entry) => {
        const first = entry.rank === 1
        const move = movements.get(entry.nickname)
        const rising = move?.kind === 'up' || move?.kind === 'new'

        return (
          <li
            key={entry.nickname}
            ref={(element) => {
              if (element) rows.current.set(entry.nickname, element)
              else rows.current.delete(entry.nickname)
            }}
            className={cn(
              'flex h-[30px] items-center gap-2 rounded-lg px-2',
              first && 'bg-brand-50',
            )}
          >
            {first ? (
              <span
                aria-hidden
                className={cn(
                  'w-[17px] shrink-0 text-center text-[14px] leading-none',
                  // 1등이 바뀐 순간에만 메달이 튄다.
                  rising && 'animate-medal',
                )}
              >
                🥇
              </span>
            ) : (
              <span
                aria-hidden
                className="w-[17px] shrink-0 text-center text-[12px] font-medium text-neutral-tertiary"
              >
                {entry.rank}
              </span>
            )}
            <span className="sr-only">{entry.rank}위</span>

            <span
              className={cn(
                'min-w-0 flex-1 truncate text-[12px]',
                first
                  ? 'font-bold text-foreground'
                  : 'font-medium text-neutral-tertiary',
              )}
            >
              {entry.nickname}
              {entry.isMe && (
                <span className="ml-1 text-[11px] font-bold text-brand-600">
                  나
                </span>
              )}
            </span>

            {move && <MovementBadge movement={move} />}

            <span
              className={cn(
                'shrink-0 text-[12px] tabular-nums',
                first
                  ? 'font-bold text-brand-500'
                  : 'font-medium text-neutral-tertiary',
              )}
            >
              {formatWon(entry.amount)}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

/** 순위가 오른 줄에 잠깐 깔리는 배경. 어느 줄이 바뀌었는지 눈에 남긴다. */
function flash(element: HTMLElement) {
  element.animate(
    [
      {
        backgroundColor: 'var(--brand-200)',
        boxShadow: 'inset 0 0 0 1px var(--brand-300)',
      },
      {
        backgroundColor: 'var(--brand-200)',
        boxShadow: 'inset 0 0 0 1px var(--brand-300)',
        offset: 0.5,
      },
      {
        backgroundColor: 'transparent',
        boxShadow: 'inset 0 0 0 1px transparent',
      },
    ],
    { duration: 1200, easing: 'ease-out' },
  )
}

/** `▲2` `▼1` `NEW`. 몇 초 뒤 사라진다. */
function MovementBadge({ movement }: { movement: Movement }) {
  const label =
    movement.kind === 'new'
      ? 'NEW'
      : `${movement.kind === 'up' ? '▲' : '▼'}${movement.delta}`

  return (
    <span
      className={cn(
        'animate-badge-pop flex h-[17px] shrink-0 items-center rounded-full px-1.5 text-[10px] font-extrabold tabular-nums',
        movement.kind === 'down'
          ? 'bg-fill text-neutral-tertiary'
          : 'bg-result-won-surface text-result-won',
      )}
    >
      {label}
      <span className="sr-only">
        {movement.kind === 'new'
          ? ' 새로 진입'
          : movement.kind === 'up'
            ? ` ${movement.delta}계단 상승`
            : ` ${movement.delta}계단 하락`}
      </span>
    </span>
  )
}
