import { Loader2, X } from 'lucide-react'

import { formatWon } from '@/lib/format'
import type { AuctionItemDetail } from '@/mocks/types'

/**
 * 입찰 확인 패널 (Figma `WEB-15 · 구매자 · 입찰 확인 모달`).
 *
 * 화면 가운데 뜨는 모달이 아니라 오른쪽 열 자리에 들어오는 패널이다.
 * 확정 전까지는 어떤 것도 성공으로 표시하지 않는다.
 */
export function BidConfirmPanel({
  item,
  amount,
  pending,
  onConfirm,
  onCancel,
}: {
  item: AuctionItemDetail
  amount: number
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const diff = amount - item.currentPrice

  return (
    <div className="flex h-full flex-col rounded-[20px] bg-card p-2">
      <div className="flex items-start gap-2 px-2 pt-2">
        <div className="min-w-0">
          <h2 className="text-[17px] font-bold text-foreground">입찰 확인</h2>
          <p className="mt-1 text-[12px] font-medium text-neutral-tertiary">
            입찰 전 금액을 확인해 주세요
          </p>
        </div>

        <button
          type="button"
          onClick={onCancel}
          disabled={pending}
          aria-label="입찰 확인 닫기"
          className="ease-soft ml-auto flex size-7 shrink-0 items-center justify-center rounded-full text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-50"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      <div className="mt-5 px-2">
        <h3 className="text-[15px] font-bold text-foreground">{item.name}</h3>

        <p className="mt-3 text-[12px] font-medium text-neutral-tertiary">
          입찰 금액
        </p>
        <p className="mt-1 text-[24px] font-extrabold tabular-nums text-brand-500">
          {formatWon(amount)}
        </p>

        <div className="mt-6 rounded-xl bg-surface-subtle px-4 py-3">
          <p className="text-[13px] font-semibold text-foreground">
            현재가 {formatWon(item.currentPrice)} → {formatWon(amount)}
          </p>
          <p className="mt-1.5 text-[12px] font-medium text-neutral-tertiary">
            +{diff.toLocaleString('ko-KR')}원 반영
          </p>
        </div>
      </div>

      <p className="mt-auto px-2 pb-3 text-center text-[11px] font-normal text-neutral-muted">
        확인하면 즉시 입찰에 반영됩니다.
      </p>

      <div className="flex gap-2 px-2 pb-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={pending}
          className="ease-soft h-11 flex-1 rounded-xl border bg-card text-[14px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.98] disabled:opacity-50"
        >
          취소
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={pending}
          className="ease-soft flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-primary text-[14px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100"
        >
          {pending && <Loader2 aria-hidden className="size-4 animate-spin" />}
          {pending ? '처리 중…' : '입찰 확정'}
        </button>
      </div>
    </div>
  )
}
