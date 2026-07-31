import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import type { LeaderboardEntry } from '@/mocks/types'

/**
 * 리더보드 순위 행.
 *
 * 라이브 오른쪽 열의 물품별 리더보드와 물품 상세의 실시간 리더보드가
 * 같은 모양을 쓰도록 한 곳에 둔다. 상위 3명만 보여준다.
 */
export function LeaderboardRows({
  entries,
  emptyText = '아직 입찰이 없어요',
}: {
  entries: LeaderboardEntry[]
  emptyText?: string
}) {
  const top3 = entries.slice(0, 3)

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
        return (
          <li
            key={entry.rank}
            className={cn(
              'flex h-[30px] items-center gap-2 rounded-lg px-2',
              first && 'bg-brand-50',
            )}
          >
            {first ? (
              <span
                aria-hidden
                className="w-[17px] shrink-0 text-center text-[14px] leading-none"
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
