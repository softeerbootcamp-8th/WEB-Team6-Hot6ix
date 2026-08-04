import { LiveItemCard } from '@/features/live/components/live-item-card'
import { useListFlip } from '@/features/live/use-list-flip'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail, ItemStatus } from '@/mocks/types'

/** 위에서부터 진행 중 → 시작 전 → 종료 순. 지금 볼 것이 위로 온다. */
const GROUPS: { status: ItemStatus; label: string; dot: string }[] = [
  { status: 'ACTIVE', label: '진행 중', dot: 'bg-live' },
  { status: 'READY', label: '시작 전', dot: 'bg-notice' },
  { status: 'CLOSED', label: '경매 종료', dot: 'bg-neutral-muted' },
]

/**
 * 라이브 왼쪽 열의 물품 목록.
 *
 * 상태별로 묶어서 보여준다. 한 줄로 죽 늘어놓으면 진행 중인 물품이
 * 어디 있는지 매번 찾아야 한다. 비어 있는 그룹은 머리글도 그리지 않는다.
 */
export function LiveItemList({
  items,
  canStart = false,
  isSelected,
  isDimmed,
  justClosedId,
  startingItemId,
  className,
  onSelect,
  onStart,
}: {
  items: AuctionItemDetail[]
  canStart?: boolean
  isSelected?: (item: AuctionItemDetail) => boolean
  isDimmed?: (item: AuctionItemDetail) => boolean
  /** 방금 마감된 물품 id. 도장 애니메이션에 쓴다. */
  justClosedId?: number | null
  /** 시작 요청이 서버에서 처리 중인 물품 id */
  startingItemId?: number | null
  /** 바깥 레이아웃에 맞춰 스크롤 영역 크기를 바꿀 때 쓴다. */
  className?: string
  onSelect: (item: AuctionItemDetail) => void
  onStart?: (item: AuctionItemDetail, minutes: number) => void
}) {
  // 상태가 바뀌면 묶음이 달라진다. 그 이동을 눈에 보이게 한다.
  const rows = useListFlip<HTMLLIElement>(
    items.map((item) => `${item.id}:${item.status}`).join('|'),
  )

  return (
    <div
      className={cn(
        'mt-2.5 space-y-3 overflow-y-auto overscroll-contain pr-0.5',
        className ?? 'max-h-[420px] lg:max-h-none lg:min-h-0 lg:flex-1',
      )}
    >
      {GROUPS.map((group) => {
        const grouped = items.filter((item) => item.status === group.status)
        if (grouped.length === 0) return null

        return (
          <section key={group.status}>
            <h3 className="flex items-center gap-1.5 px-1 pb-1.5 text-[11px] font-bold text-neutral-tertiary">
              <span
                aria-hidden
                className={cn('size-[6px] rounded-full', group.dot)}
              />
              {group.label}
              <span className="tabular-nums text-neutral-muted">
                {grouped.length}
              </span>
            </h3>

            <ul className="space-y-2">
              {grouped.map((item) => (
                <LiveItemCard
                  key={item.id}
                  item={item}
                  rowRef={(element) => {
                    if (element) rows.current.set(item.id, element)
                    else rows.current.delete(item.id)
                  }}
                  justClosed={justClosedId === item.id}
                  canStart={canStart}
                  starting={startingItemId === item.id}
                  selected={isSelected?.(item) ?? false}
                  dimmed={isDimmed?.(item) ?? false}
                  onSelect={() => onSelect(item)}
                  onStart={
                    onStart ? (minutes) => onStart(item, minutes) : undefined
                  }
                />
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
