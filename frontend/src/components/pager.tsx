import { cn } from '@/lib/utils'

/**
 * 페이지네이션.
 *
 * Figma 거래 상세(`WEB-02`/`WEB-03`)와 상품 관리(`WEB-05`)가 같은 모양을 쓴다.
 * 화살표는 `‹` `›` 글리프, 현재 페이지만 브랜드색으로 채운다.
 */
/**
 * 그릴 쪽 번호. 쪽이 많으면 첫 쪽과 마지막 쪽, 그리고 현재 쪽 주변만 남기고
 * 사이를 `gap`(…)으로 접는다. 전부 그리면 14쪽만 돼도 한 줄이 700px 가까이
 * 되어 폰 폭을 넘고, 그게 화면 전체를 가로로 밀어낸다.
 *
 * 7쪽 이하는 접어봐야 줄어드는 게 없어서 그냥 다 그린다.
 */
function pageSlots(page: number, pageCount: number): ('gap' | number)[] {
  if (pageCount <= 7)
    return Array.from({ length: pageCount }, (_, index) => index)

  const last = pageCount - 1

  if (page <= 3) return [0, 1, 2, 3, 4, 'gap', last]
  if (page >= pageCount - 4)
    return [0, 'gap', last - 4, last - 3, last - 2, last - 1, last]
  return [0, 'gap', page - 1, page, page + 1, 'gap', last]
}

export function Pager({
  page,
  pageCount,
  onChange,
  meta,
  size = 36,
  className,
}: {
  /** 0-based */
  page: number
  pageCount: number
  onChange: (next: number) => void
  /** 오른쪽 끝에 붙는 보조 문구. 예) "총 12명 · 5명씩" */
  meta?: string
  /** 버튼 한 변. Figma 는 거래 상세 36, 상품 관리 40. */
  size?: 36 | 40
  className?: string
}) {
  const step = (delta: number) =>
    onChange(Math.min(pageCount - 1, Math.max(0, page + delta)))

  const box = size === 40 ? 'size-10' : 'size-9'

  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)}>
      {/*
        접어도 아주 좁은 폰에서는 한 줄에 안 들어갈 수 있다. 그때 넘치게 두면
        화면 전체가 가로로 밀리므로, 넘치는 대신 다음 줄로 접히게 한다.
      */}
      <div className="flex flex-1 flex-wrap items-center justify-center gap-2">
        <button
          type="button"
          aria-label="이전 페이지"
          disabled={page === 0}
          onClick={() => step(-1)}
          className={cn(
            'ease-soft rounded-full text-[18px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-90 disabled:pointer-events-none disabled:opacity-40',
            box,
          )}
        >
          ‹
        </button>

        {pageSlots(page, pageCount).map((slot, index) =>
          slot === 'gap' ? (
            <span
              key={`gap-${index}`}
              aria-hidden
              className="w-4 text-center text-[13px] font-bold text-neutral-muted"
            >
              …
            </span>
          ) : (
            <button
              key={slot}
              type="button"
              aria-current={page === slot ? 'page' : undefined}
              onClick={() => onChange(slot)}
              className={cn(
                'ease-soft rounded-full text-[13px] font-bold transition-all duration-150 active:scale-90',
                box,
                page === slot
                  ? 'bg-brand-500 text-white'
                  : 'text-neutral-tertiary hover:bg-fill',
              )}
            >
              {slot + 1}
            </button>
          ),
        )}

        <button
          type="button"
          aria-label="다음 페이지"
          disabled={page === pageCount - 1}
          onClick={() => step(1)}
          className={cn(
            'ease-soft rounded-full text-[18px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-90 disabled:pointer-events-none disabled:opacity-40',
            box,
          )}
        >
          ›
        </button>
      </div>

      {meta && (
        <p className="ml-auto text-[12px] font-medium text-neutral-tertiary">
          {meta}
        </p>
      )}
    </div>
  )
}
