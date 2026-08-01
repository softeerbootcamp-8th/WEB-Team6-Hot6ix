import { ArrowRight, Loader2, X } from 'lucide-react'

import { formatWon } from '@/lib/format'
import type { AuctionItemDetail } from '@/mocks/types'

/**
 * 입찰 확인 패널 (Figma `WEB-15 · 구매자 · 입찰 확인 모달`).
 *
 * 화면 가운데 뜨는 모달이 아니라 오른쪽 열 자리에 들어오는 패널이다.
 * 확정 전까지는 어떤 것도 성공으로 표시하지 않는다.
 *
 * 머리글은 가운데로 모으고, 금액 박스 안에서는 파랑 위에 파랑을 쓰지 않는다.
 * 옅은 파란 배경에 파란 글씨를 얹으면 숫자가 배경에 묻혀 읽히지 않는다.
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
    <div className="flex h-full flex-col rounded-[20px] bg-card p-4">
      {/* 닫기는 오른쪽 위 고정, 제목은 열 가운데 */}
      <div className="relative shrink-0 px-9 pt-1 text-center">
        <h2 className="text-[17px] font-bold text-foreground">입찰 확인</h2>
        <p className="mt-1 truncate text-[12px] font-medium text-neutral-tertiary">
          {item.name}
        </p>

        <button
          type="button"
          onClick={onCancel}
          disabled={pending}
          aria-label="입찰 확인 닫기"
          className="ease-soft absolute top-0 right-0 flex size-8 items-center justify-center rounded-full text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-50"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      {/* 금액이 이 패널의 주인공이다. 남는 세로 공간을 여기에 준다. */}
      <div className="mt-3 flex min-h-0 flex-1 flex-col items-center justify-center rounded-2xl border border-brand-200 bg-card px-4 py-6 text-center">
        <p className="text-[12px] font-bold text-neutral-tertiary">입찰 금액</p>
        <p className="mt-2 text-[34px] leading-none font-extrabold tabular-nums text-brand-600">
          {formatWon(amount)}
        </p>

        <p className="mt-6 flex items-center gap-2 rounded-full bg-fill px-3 py-1.5 text-[13px] font-semibold text-neutral-secondary">
          <span className="tabular-nums">{formatWon(item.currentPrice)}</span>
          <ArrowRight aria-hidden className="size-3.5 text-neutral-muted" />
          <span className="tabular-nums text-foreground">
            {formatWon(amount)}
          </span>
        </p>
        <p className="mt-2 text-[12px] font-semibold text-brand-600">
          현재가보다 +{diff.toLocaleString('ko-KR')}원
        </p>
      </div>

      <p className="mt-3 shrink-0 text-center text-[11px] font-medium text-neutral-muted">
        확인하면 즉시 입찰에 반영됩니다.
      </p>

      <div className="mt-3 flex shrink-0 gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={pending}
          className="ease-soft h-12 flex-1 rounded-xl border bg-card text-[14px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.98] disabled:opacity-50"
        >
          취소
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={pending}
          className="ease-soft flex h-12 flex-[1.4] items-center justify-center gap-2 rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100"
        >
          {pending && <Loader2 aria-hidden className="size-4 animate-spin" />}
          {pending ? '처리 중…' : '입찰 확정'}
        </button>
      </div>
    </div>
  )
}
