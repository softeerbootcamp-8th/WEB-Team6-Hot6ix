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

/**
 * 전화번호 하이픈 자동 삽입.
 *
 * 입력하는 도중에도 자리수에 맞춰 끊어준다(토스 등에서 익숙한 방식).
 * 휴대전화(010…)와 서울 지역번호(02…)를 함께 처리한다. 숫자가 아닌 글자는
 * 버리므로 붙여넣기한 `010.1234.5678` 같은 값도 정리된다.
 */
export function formatPhoneNumber(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11)

  // 02 는 국번이 한 자리 짧다.
  if (digits.startsWith('02')) {
    if (digits.length <= 2) return digits
    if (digits.length <= 5) return `${digits.slice(0, 2)}-${digits.slice(2)}`
    if (digits.length <= 9) {
      return `${digits.slice(0, 2)}-${digits.slice(2, 5)}-${digits.slice(5)}`
    }
    return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6, 10)}`
  }

  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  if (digits.length <= 10) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`
}
