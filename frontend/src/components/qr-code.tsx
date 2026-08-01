import { useEffect, useRef, useState } from 'react'

import { cn } from '@/lib/utils'
import { drawQr } from '@/lib/qr'

/**
 * 공유 링크를 QR 로 그린다. 서버는 QR 이미지를 만들지 않고 URL 문자열만 주므로
 * 화면에 보이는 QR 은 전부 여기서 그린다. PNG 저장은 `lib/qr` 의
 * `downloadQrCard` 가 같은 코드로 처리한다.
 */
export function QrCode({
  value,
  size = 180,
  className,
  label = '경매방 참여 QR 코드',
}: {
  /** 아직 안 받아왔으면 undefined — 자리만 잡아 둔다. */
  value: string | undefined
  size?: number
  className?: string
  label?: string
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !value) return

    let cancelled = false

    drawQr(canvas, value, size)
      .then(() => {
        if (!cancelled) setFailed(false)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })

    return () => {
      cancelled = true
    }
  }, [value, size])

  if (!value || failed) {
    return (
      <span
        role="img"
        aria-label={
          failed ? 'QR 코드를 만들지 못했어요' : 'QR 코드 불러오는 중'
        }
        style={{ width: size, height: size }}
        className={cn(
          'block rounded-2xl bg-fill',
          !failed && 'animate-skeleton',
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
      style={{ width: size, height: size }}
      className={cn('rounded-2xl', className)}
    />
  )
}
