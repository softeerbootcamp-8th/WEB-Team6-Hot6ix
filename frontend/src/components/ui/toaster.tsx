import { AlertCircle, Check, Info, X } from 'lucide-react'
import { useEffect } from 'react'

import { BidAcceptedMotion } from '@/components/BidAcceptedMotion'
import { cn } from '@/lib/utils'
import { toast, useToasts, type Toast, type ToastTone } from '@/lib/toast'

/**
 * 전역 알림 렌더러. `__root.tsx` 에 한 번만 둔다.
 *
 * **화면 오른쪽 위**에 뜬다. 아래쪽은 라이브 화면의 입찰 바와 겹친다.
 * 색과 모양은 Figma `WEB-17 · 입찰 성공` / `WEB-18 · 입찰 실패` 기준이다.
 */
/*
 * 흰 배경에 색 테두리·색 글씨.
 * 면 전체를 색으로 채우면 화면에서 너무 튀고 글자도 읽기 어렵다.
 */
const TONE: Record<
  ToastTone,
  { className: string; accent: string; icon: React.ReactNode }
> = {
  success: {
    className: 'border-success/40 text-success',
    accent: 'bg-success-surface',
    icon: <Check className="size-4" strokeWidth={3} />,
  },
  error: {
    className: 'border-live/40 text-live',
    accent: 'bg-live-surface',
    icon: <AlertCircle className="size-4" />,
  },
  info: {
    className: 'border-border-strong text-neutral-secondary',
    accent: 'bg-fill',
    icon: <Info className="size-4" />,
  },
}

export function Toaster() {
  const toasts = useToasts()

  if (toasts.length === 0) return null

  return (
    <div
      role="status"
      aria-live="polite"
      // 모바일은 앱바(56~64) 아래로 내려야 제목과 겹치지 않는다.
      className="pointer-events-none fixed inset-x-0 top-[76px] z-50 flex flex-col items-end gap-2 px-4 md:top-6 md:px-6"
    >
      {toasts.map((item) => (
        <ToastRow key={item.id} toast={item} />
      ))}
    </div>
  )
}

function ToastRow({ toast: item }: { toast: Toast }) {
  const tone = TONE[item.tone]

  // 자동 닫기. 화면을 떠나거나 사용자가 먼저 닫으면 타이머를 정리한다.
  useEffect(() => {
    if (item.duration <= 0) return
    const timer = window.setTimeout(() => toast.dismiss(item.id), item.duration)
    return () => window.clearTimeout(timer)
  }, [item.id, item.duration])

  return (
    <div
      className={cn(
        'animate-toast pointer-events-auto flex w-full max-w-[380px] items-start gap-3 rounded-2xl border bg-card px-4 py-3.5 shadow-lg',
        tone.className,
      )}
    >
      {/*
       * 연출이 붙은 토스트는 아이콘 자리를 그림이 대신한다. 체크 아이콘까지
       * 같이 두면 성공 표시가 둘이 된다.
       */}
      {item.motion === 'bidAccepted' ? (
        <BidAcceptedMotion size={44} />
      ) : (
        <span
          aria-hidden
          className={cn(
            'mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full',
            tone.accent,
          )}
        >
          {tone.icon}
        </span>
      )}

      <div className="min-w-0 flex-1 self-center">
        <p className="text-[14px] font-bold">{item.message}</p>
        {item.description && (
          <p className="mt-1 text-[12px] font-medium text-neutral-tertiary">
            {item.description}
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={() => toast.dismiss(item.id)}
        aria-label="알림 닫기"
        className="ease-soft -mr-1 flex size-6 shrink-0 items-center justify-center rounded-full text-neutral-muted transition-all duration-150 hover:bg-fill hover:text-neutral-secondary active:scale-95"
      >
        <X aria-hidden className="size-3.5" />
      </button>
    </div>
  )
}
