import { Check, Copy, Download, X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { QrCode } from '@/components/qr-code'
import { downloadQrCard } from '@/lib/qr'
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
  shareCode,
  roomTitle,
  onClose,
}: {
  shareCode: string
  roomTitle: string
  onClose: () => void
}) {
  const [copied, setCopied] = useState(false)

  /*
   * 링크를 화면에서 조립한다. 예전에는 서버가 조립해 주는 소유자 전용
   * `GET /{roomId}/share` 를 불렀는데, 이 화면은 게스트도 여는 곳이라
   * 게스트가 공유 버튼을 누르면 401 이 나서 링크가 비어 있었다.
   *
   * 방 주소가 곧 공유 코드가 되면서 화면이 이미 코드를 들고 있으므로
   * 요청 없이 만든다. 기준이 서버의 `app.frontend-url` 이 아니라 지금 보고 있는
   * 도메인이라, 프리뷰 배포에서 복사한 링크가 운영을 가리키는 일도 없어진다.
   */
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
            className="ease-soft flex h-11 shrink-0 items-center gap-1.5 rounded-xl border bg-card px-3.5 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95 disabled:pointer-events-none disabled:opacity-40"
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

/** QR 카드와 이미지 저장. */
function ShareQr({
  roomTitle,
  shareUrl,
}: {
  roomTitle: string
  shareUrl: string
}) {
  const save = async () => {
    try {
      await downloadQrCard(shareUrl, roomTitle)
      toast.success('공유 이미지를 저장했어요')
    } catch {
      toast.error('이미지를 만들지 못했어요')
    }
  }

  return (
    <div className="mt-3 flex min-h-0 flex-1 flex-col items-center justify-center rounded-2xl bg-surface-subtle p-4">
      {/*
       * 자리에 맞춰 늘어난다. 모바일 모달은 높이가 680px 로 고정(퀵입찰 목록
       * 때문)이라 QR 이 180px 이면 위아래가 휑했다. 남는 폭을 먹게 두고,
       * 데스크톱 오른쪽 열에서는 열 너비가 상한이 된다.
       *
       * 상한은 카드 폭의 80%(최대 224px)다. 꽉 채우면 카드 안이 QR 하나로
       * 가득 차 보인다. 이 정도면 화면으로 스캔하는 데 지장이 없다.
       */}
      <QrCode value={shareUrl} className="max-h-full max-w-[min(80%,224px)]" />

      <button
        type="button"
        onClick={save}
        className="ease-soft mt-3 flex h-10 items-center gap-1.5 rounded-xl px-4 text-[12px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95 disabled:pointer-events-none disabled:opacity-40"
      >
        <Download aria-hidden className="size-3.5" />
        이미지 저장
      </button>
    </div>
  )
}
