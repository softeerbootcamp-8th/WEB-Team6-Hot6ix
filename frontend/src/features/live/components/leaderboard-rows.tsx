import { useEffect, useLayoutEffect, useRef, useState } from 'react'

import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import type { LeaderboardEntry } from '@/types/domain'
import { leaderboardEntryKey } from '@/features/live/leaderboard-key'

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
 * 위치를 **bidderKey**로 기억하는 게 핵심이다. 등수로 기억하면 "1등 칸"의
 * 좌표는 늘 같고, 닉네임으로 기억하면 동명이인 두 줄이 하나로 합쳐진다.
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
    .map((entry) => `${leaderboardEntryKey(entry)}:${entry.rank}`)
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
        const key = leaderboardEntryKey(entry)
        const before = ranks.current.get(key)
        if (before === undefined) {
          changed.set(key, { kind: 'new', delta: 0 })
        } else if (before > entry.rank) {
          changed.set(key, {
            kind: 'up',
            delta: before - entry.rank,
          })
        } else if (before < entry.rank) {
          changed.set(key, {
            kind: 'down',
            delta: entry.rank - before,
          })
        }
      })
    }
    ranks.current = new Map(
      top3.map((entry) => [leaderboardEntryKey(entry), entry.rank]),
    )

    rows.current.forEach((element, nickname) => {
      /*
       * **뷰포트가 아니라 `offsetTop` 으로 잰다.** 예전에는
       * `getBoundingClientRect().top` 을 기억했는데, 그 값은 스크롤 위치가 섞인
       * 좌표라 목록이 스크롤되거나 위쪽 레이아웃이 밀리면 이전 값과 기준이
       * 달라진다. 한 칸 움직였을 뿐인데 수백 px 을 이동한 것으로 계산돼서, 줄이
       * 카드 밖 제목·현재가 자리까지 솟아올랐다.
       */
      const next = element.offsetTop
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

      /*
       * 줄 높이(30px)와 간격을 감안하면 세 줄짜리 목록에서 나올 수 있는 이동은
       * 100px 을 넘지 않는다. 그보다 크면 위치를 잘못 잰 것이므로 그냥 새 자리에
       * 둔다. 틀린 거리로 날아가는 것보다 안 움직이는 편이 낫다.
       */
      if (Math.abs(delta) > 100) return

      /*
       * 먼저 돌던 애니메이션을 걷어낸다. 입찰이 몰리면 이전 것이 끝나기 전에
       * 다음 것이 걸리는데, 밀려난 줄은 `fill: backwards` 로 시작 위치에
       * 붙들려 있어서 그대로 어긋난 자리에 멈춰 있었다.
       *
       * **자리를 옮기는 줄만 걷어낸다.** 안 움직이는 줄까지 걷어내면 직전에
       * 켜진 강조 배경(`flash`, 1.2초)이 다음 입찰에서 잘려서, 입찰이 몰릴수록
       * 강조가 거의 안 보인다.
       */
      element.getAnimations().forEach((animation) => animation.cancel())

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
        const entryKey = leaderboardEntryKey(entry)
        const first = entry.rank === 1
        const move = movements.get(entryKey)
        const rising = move?.kind === 'up' || move?.kind === 'new'

        return (
          <li
            key={entryKey}
            ref={(element) => {
              if (element) rows.current.set(entryKey, element)
              else rows.current.delete(entryKey)
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
