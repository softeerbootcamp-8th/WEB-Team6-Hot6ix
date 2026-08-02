import { ImageOff } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { cn } from '@/lib/utils'
import { drawQr } from '@/lib/qr'

/**
 * 공유 링크를 QR 로 그린다. 서버는 QR 이미지를 만들지 않고 URL 문자열만 주므로
 * 화면에 보이는 QR 은 전부 여기서 그린다. PNG 저장은 `lib/qr` 의
 * `downloadQrCard` 가 같은 코드로 처리한다.
 *
 * 표시 크기는 인라인 style 이 아니라 className 으로 정한다 — 자리에 맞춰
 * 늘어나야 하는 곳(모바일 공유 모달)과 고정이어야 하는 곳(생성 완료 카드)이
 * 같이 있어서다. canvas 자체는 항상 아래 해상도로 그리고 CSS 가 줄여 쓰므로,
 * 고해상도 화면에서 커져도 뭉개지지 않는다.
 */
const RENDER_SIZE = 640

export function QrCode({
  value,
  error = false,
  className,
  label = '경매방 참여 QR 코드',
}: {
  /** 아직 안 받아왔으면 undefined — 자리만 잡아 둔다. */
  value: string | undefined
  /**
   * 링크를 못 받아온 상태(요청 실패·권한 없음 등). 이걸 안 넘기면 응답이
   * 영영 안 와도 로딩 스켈레톤이 계속 깜빡여 "불러오는 중"처럼 보인다.
   */
  error?: boolean
  /** 표시 크기. 예: `size-40`(고정) 또는 `max-w-[min(100%,280px)]`(가변) */
  className?: string
  label?: string
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [drawFailed, setDrawFailed] = useState(false)
  const failed = error || drawFailed

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !value) return

    let cancelled = false

    drawQr(canvas, value, RENDER_SIZE)
      .then(() => {
        if (!cancelled) setDrawFailed(false)
      })
      .catch(() => {
        if (!cancelled) setDrawFailed(true)
      })

    return () => {
      cancelled = true
    }
  }, [value])

  if (failed) {
    return (
      <span
        role="img"
        aria-label="QR 코드를 만들지 못했어요"
        className={cn(
          'flex aspect-square w-full items-center justify-center rounded-2xl bg-fill text-neutral-muted',
          className,
        )}
      >
        <ImageOff aria-hidden className="size-1/3" />
      </span>
    )
  }

  if (!value) {
    return (
      <span
        role="img"
        aria-label="QR 코드 불러오는 중"
        className={cn(
          'animate-skeleton block aspect-square w-full rounded-2xl bg-fill',
          className,
        )}
      />
    )
  }

  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={label}
      className={cn('block rounded-2xl', className)}
    />
  )
}
