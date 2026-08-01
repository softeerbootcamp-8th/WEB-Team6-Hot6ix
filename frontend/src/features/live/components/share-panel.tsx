import { Check, Copy, Download, QrCode, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { toast } from '@/lib/toast'

/**
 * 경매방 공유 패널 (Figma `WEB-09A · 구매자 · 라이브 / 경매방 공유`).
 *
 * 방 헤더의 공유 버튼을 누르면 오른쪽 열 자리에 들어온다.
 * 링크만 있으면 비로그인도 둘러볼 수 있다는 점을 함께 안내한다.
 *
 * QR 은 열 높이를 채우도록 가운데에 크게 둔다. 예전에는 위쪽에만 몰려
 * 있고 아래가 비어 있었다.
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

  // "복사됨" 표시를 되돌리는 타이머. 패널이 닫히면 정리한다.
  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 2000)
    return () => window.clearTimeout(timer)
  }, [copied])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl)
      setCopied(true)
    } catch {
      // 클립보드 권한이 없으면 사용자가 직접 선택해 복사할 수 있게 둔다.
      toast.error('링크를 복사하지 못했어요', {
        description: '주소를 선택해 직접 복사해 주세요.',
      })
    }
  }

  return (
    <div className="flex h-full flex-col rounded-[20px] bg-card p-4">
      <div className="flex shrink-0 items-start gap-2">
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
          className="ease-soft ml-auto flex size-8 shrink-0 items-center justify-center rounded-full text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      <ShareQr roomTitle={roomTitle} shareUrl={shareUrl} />

      <div className="mt-3 shrink-0">
        <p className="text-[12px] font-medium text-neutral-tertiary">
          참여 링크
        </p>
        <div className="mt-2 flex gap-2">
          <input
            readOnly
            value={shareUrl}
            aria-label="참여 링크"
            onFocus={(event) => event.currentTarget.select()}
            className="h-11 min-w-0 flex-1 rounded-xl border bg-surface-subtle px-3 text-[12px] font-medium text-neutral-secondary outline-none"
          />
          <button
            type="button"
            onClick={copy}
            className="ease-soft flex h-11 shrink-0 items-center gap-1.5 rounded-xl border bg-card px-3.5 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
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

/**
 * QR 카드와 이미지 저장.
 *
 * QR 은 아직 실제 코드가 아니라 자리 표시다. 저장 버튼은 지금 화면에 그린
 * 카드를 그대로 canvas 로 옮겨 PNG 로 내려받는다. 실제 QR 생성이 붙으면
 * 이 canvas 에 코드만 얹으면 된다.
 */
function ShareQr({
  roomTitle,
  shareUrl,
}: {
  roomTitle: string
  shareUrl: string
}) {
  const urlRef = useRef<string | null>(null)

  // 만들어 둔 blob URL 은 화면을 떠날 때 해제한다.
  useEffect(
    () => () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current)
    },
    [],
  )

  const save = () => {
    const size = 640
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const context = canvas.getContext('2d')
    if (!context) {
      toast.error('이미지를 만들지 못했어요')
      return
    }

    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, size, size)

    context.fillStyle = '#eff6ff'
    context.fillRect(80, 120, size - 160, size - 320)

    context.fillStyle = '#191f28'
    context.font = 'bold 34px Pretendard, sans-serif'
    context.textAlign = 'center'
    context.fillText(roomTitle.slice(0, 18), size / 2, 80)

    context.fillStyle = '#3182f6'
    context.font = '600 24px Pretendard, sans-serif'
    context.fillText('QR 자리', size / 2, size / 2)

    context.fillStyle = '#6b7684'
    context.font = '500 22px Pretendard, sans-serif'
    context.fillText(shareUrl, size / 2, size - 60)

    canvas.toBlob((blob) => {
      if (!blob) {
        toast.error('이미지를 만들지 못했어요')
        return
      }
      if (urlRef.current) URL.revokeObjectURL(urlRef.current)
      urlRef.current = URL.createObjectURL(blob)

      const link = document.createElement('a')
      link.href = urlRef.current
      link.download = `upbid-${roomTitle}.png`
      link.click()
      toast.success('공유 이미지를 저장했어요')
    }, 'image/png')
  }

  return (
    <div className="mt-3 flex min-h-0 flex-1 flex-col items-center justify-center rounded-2xl bg-surface-subtle p-4">
      <span
        aria-hidden
        className="flex aspect-square w-full max-w-[180px] items-center justify-center rounded-2xl bg-card text-neutral-muted"
      >
        {/* TODO: 실제 QR 생성은 별도 이슈. 지금은 자리 표시다. */}
        <QrCode className="size-24" />
      </span>

      <p className="mt-3 text-center text-[12px] font-medium text-neutral-tertiary">
        QR 코드 · 오프라인에서 바로 공유
      </p>

      <button
        type="button"
        onClick={save}
        className="ease-soft mt-3 flex h-10 items-center gap-1.5 rounded-xl border bg-card px-4 text-[12px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95"
      >
        <Download aria-hidden className="size-3.5" />
        이미지 저장
      </button>
    </div>
  )
}
