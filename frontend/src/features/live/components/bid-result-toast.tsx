import { AlertCircle, Check, X } from 'lucide-react'
import { useEffect } from 'react'

import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'

export interface BidResult {
  tone: 'success' | 'error'
  itemName: string
  amount: number
  message: string
}

/**
 * 입찰 결과 알림 (Figma `WEB-17 · 구매자 · 입찰 성공` / `WEB-18 · 입찰 실패`).
 *
 * 화면 아래에서 올라오는 토스트다. 성공은 초록, 실패는 빨강이고
 * 몇 초 뒤 저절로 사라진다. 결과는 서버 응답을 받은 뒤에만 띄운다.
 */
export function BidResultToast({
  result,
  onClose,
  duration = 4000,
}: {
  result: BidResult | null
  onClose: () => void
  duration?: number
}) {
  // 일정 시간이 지나면 자동으로 닫는다. 화면을 떠나면 타이머를 정리한다.
  useEffect(() => {
    if (!result) return
    const timer = window.setTimeout(onClose, duration)
    return () => window.clearTimeout(timer)
  }, [result, duration, onClose])

  if (!result) return null

  const success = result.tone === 'success'

  return (
    <div
      role="status"
      aria-live="polite"
      className="animate-rise pointer-events-none fixed inset-x-0 bottom-8 z-50 flex justify-center px-5"
    >
      <div
        className={cn(
          'pointer-events-auto flex w-full max-w-[420px] items-start gap-3 rounded-2xl px-4 py-3.5 text-white shadow-lg',
          success ? 'bg-success' : 'bg-live',
        )}
      >
        <span
          aria-hidden
          className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-white/20"
        >
          {success ? (
            <Check className="size-4" strokeWidth={3} />
          ) : (
            <AlertCircle className="size-4" />
          )}
        </span>

        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-bold">{result.message}</p>
          <p className="mt-1 text-[12px] font-medium text-white/85">
            {result.itemName} · {formatWon(result.amount)}
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          aria-label="알림 닫기"
          className="ease-soft -mr-1 flex size-6 shrink-0 items-center justify-center rounded-full text-white/80 transition-all duration-150 hover:bg-white/15 hover:text-white active:scale-95"
        >
          <X aria-hidden className="size-3.5" />
        </button>
      </div>
    </div>
  )
}
