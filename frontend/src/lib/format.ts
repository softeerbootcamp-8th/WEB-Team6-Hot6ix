/** 금액은 원 단위 정수다. 표시할 때만 천 단위로 끊는다. */
export function formatWon(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`
}

/** 2026.07.18 형태 */
export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date
    .toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
    .replace(/\.$/, '')
    .replace(/\.\s/g, '.')
}

/** 오후 2:47:08 형태. 실시간 이벤트 타임스탬프에 쓴다. */
export function formatTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleTimeString('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
  })
}

/** 남은 시간을 hh:mm:ss 로. Figma 카운트다운 표기(`00:12:10`)를 따른다. */
export function formatRemaining(totalSeconds: number): string {
  const safe = Math.max(0, totalSeconds)
  const pad = (value: number) => String(value).padStart(2, '0')

  return [Math.floor(safe / 3600), Math.floor((safe % 3600) / 60), safe % 60]
    .map(pad)
    .join(':')
}
