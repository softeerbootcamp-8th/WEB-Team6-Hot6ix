import { cn } from '@/lib/utils'

/**
 * 페이지네이션.
 *
 * Figma 거래 상세(`WEB-02`/`WEB-03`)와 상품 관리(`WEB-05`)가 같은 모양을 쓴다.
 * 화살표는 `‹` `›` 글리프, 현재 페이지만 브랜드색으로 채운다.
 */
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
      <div className="flex flex-1 items-center justify-center gap-2">
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

        {Array.from({ length: pageCount }, (_, index) => (
          <button
            key={index}
            type="button"
            aria-current={page === index ? 'page' : undefined}
            onClick={() => onChange(index)}
            className={cn(
              'ease-soft rounded-full text-[13px] font-bold transition-all duration-150 active:scale-90',
              box,
              page === index
                ? 'bg-brand-500 text-white'
                : 'text-neutral-tertiary hover:bg-fill',
            )}
          >
            {index + 1}
          </button>
        ))}

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
