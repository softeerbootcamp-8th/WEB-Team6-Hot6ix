import { Check, Copy, QrCode, X } from 'lucide-react'
import { useState } from 'react'

/**
 * 경매방 공유 패널 (Figma `WEB-09A · 구매자 · 라이브 / 경매방 공유`).
 *
 * 방 헤더의 공유 버튼을 누르면 오른쪽 열 자리에 들어온다.
 * 링크만 있으면 비로그인도 둘러볼 수 있다는 점을 함께 안내한다.
 */
export function SharePanel({
  roomTitle,
  shareCode,
  onClose,
}: {
  roomTitle: string
  shareCode: string
  onClose: () => void
}) {
  const [copied, setCopied] = useState(false)
  const shareUrl = `${window.location.origin}/join/${shareCode}`

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      // 클립보드 권한이 없으면 사용자가 직접 선택해 복사할 수 있게 둔다.
      setCopied(false)
    }
  }

  return (
    <div className="flex h-full flex-col rounded-[20px] bg-card p-2">
      <div className="flex items-start gap-2 px-2 pt-2">
        <div className="min-w-0">
          <h2 className="text-[17px] font-bold text-foreground">경매방 공유</h2>
          <p className="mt-1 truncate text-[12px] font-medium text-neutral-tertiary">
            {roomTitle}
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          aria-label="공유 닫기"
          className="ease-soft ml-auto flex size-7 shrink-0 items-center justify-center rounded-full text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      <div className="mt-4 min-h-0 flex-1 overflow-y-auto px-2">
        <div className="flex flex-col items-center rounded-2xl bg-surface-subtle p-5">
          <span
            aria-hidden
            className="flex size-[140px] items-center justify-center rounded-2xl bg-card text-neutral-muted"
          >
            <QrCode className="size-20" />
          </span>
          <p className="mt-3 text-[12px] font-medium text-neutral-tertiary">
            QR 코드 · 오프라인에서 바로 공유
          </p>
        </div>

        <p className="mt-4 text-[12px] font-medium text-neutral-tertiary">
          참여 링크
        </p>
        <div className="mt-2 flex gap-2">
          <input
            readOnly
            value={shareUrl}
            aria-label="참여 링크"
            onFocus={(event) => event.currentTarget.select()}
            className="h-10 min-w-0 flex-1 rounded-[10px] border bg-card px-3 text-[12px] font-medium text-neutral-secondary outline-none"
          />
          <button
            type="button"
            onClick={copy}
            className="ease-soft flex h-10 shrink-0 items-center gap-1.5 rounded-[10px] border bg-card px-3.5 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
          >
            {copied ? (
              <Check aria-hidden className="size-3.5 text-success" />
            ) : (
              <Copy aria-hidden className="size-3.5" />
            )}
            {copied ? '복사됨' : '복사'}
          </button>
        </div>

        <p aria-live="polite" className="mt-2 text-[11px] text-neutral-muted">
          {copied
            ? '링크를 복사했어요.'
            : '링크만 있으면 로그인 없이도 둘러볼 수 있어요.'}
        </p>
      </div>
    </div>
  )
}
