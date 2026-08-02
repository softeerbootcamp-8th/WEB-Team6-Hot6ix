import QRCodeLib from 'qrcode'

/**
 * QR 그리기. 화면 렌더(`QrCode` 컴포넌트)와 PNG 저장이 같은 코드를 쓰므로
 * 두 결과가 어긋나지 않는다.
 *
 * 색은 토큰을 따라가지 않고 흰 배경 + 검은 모듈로 고정한다. 다크 모드를
 * 따라가면 명암이 뒤집혀 카메라가 못 읽는다.
 */
export const QR_DARK = '#191f28'
export const QR_LIGHT = '#ffffff'

export async function drawQr(
  canvas: HTMLCanvasElement,
  value: string,
  size: number,
) {
  await QRCodeLib.toCanvas(canvas, value, {
    width: size,
    margin: 1,
    color: { dark: QR_DARK, light: QR_LIGHT },
  })

  // qrcode 는 canvas 의 style.width/height 에 픽셀 값을 직접 박는다. 그대로 두면
  // 표시 크기를 CSS 로 못 정해서 좁은 화면에서 컨테이너 밖으로 밀려 나간다.
  // 지워서 고유 크기(640×640)로 되돌리면, max-width/max-height 만으로 브라우저가
  // 비율을 지키며 줄여 준다 — canvas 는 replaced element 라 가능하다.
  canvas.style.removeProperty('width')
  canvas.style.removeProperty('height')
}

/**
 * 제목 + QR + 링크를 한 장으로 합쳐 PNG 로 내려받는다. 오프라인에서 인쇄하거나
 * SNS 에 올릴 때 쓰므로 링크 문자열도 같이 새긴다.
 */
export async function downloadQrCard(shareUrl: string, roomTitle: string) {
  const size = 640
  const qrSize = 360

  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size

  const context = canvas.getContext('2d')
  if (!context) throw new Error('canvas 2d context를 만들지 못했다')

  context.fillStyle = QR_LIGHT
  context.fillRect(0, 0, size, size)

  context.fillStyle = QR_DARK
  context.font = 'bold 34px Pretendard, sans-serif'
  context.textAlign = 'center'
  context.fillText(roomTitle.slice(0, 18), size / 2, 80)

  const qrCanvas = document.createElement('canvas')
  await QRCodeLib.toCanvas(qrCanvas, shareUrl, {
    width: qrSize,
    margin: 0,
    color: { dark: QR_DARK, light: QR_LIGHT },
  })
  context.drawImage(qrCanvas, (size - qrSize) / 2, 120, qrSize, qrSize)

  context.fillStyle = '#6b7684'
  context.font = '500 20px Pretendard, sans-serif'
  context.fillText(shareUrl, size / 2, size - 56)

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/png'),
  )
  if (!blob) throw new Error('PNG 를 만들지 못했다')

  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = `upbid-${roomTitle}.png`
  link.click()

  // click() 이 처리되기 전에 해제하면 다운로드가 취소된다. 다음 매크로태스크로 미룬다.
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
}
