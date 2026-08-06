/**
 * 소프트클로즈 연장 연출에 필요한 값.
 *
 * 서버(`SoftCloseExtended`)는 초 단위(`extendSeconds`)로 준다. 이 프로젝트의
 * 연장 폭은 30초다(`AuctionRoomDetail.softCloseSeconds`, 목업도 30).
 */
export interface SoftCloseFlash {
  itemId: number
  /** 서버가 알려준 연장 폭(초). */
  seconds: number
  /** 같은 물품이 여러 번 연장될 때 APNG 를 다시 재생시키는 값. */
  count: number
}

/**
 * 연출을 띄워두는 시간.
 *
 * v4 APNG 가 1.45초짜리라 그보다 조금 길게 잡는다. `animate-extend-stamp`
 * 지속시간과 같아야 연출이 중간에 잘리지 않는다.
 */
export const SOFT_CLOSE_FLASH_MS = 1750

/**
 * `SoftCloseAnimation` 의 `extensionMinutes` 는 분만 받는다. 30초 연장이면
 * `Math.floor(30/60)` 이 0 이라 "+0분"이 되므로, 화면에 보이는 문구는
 * `formatExtension()` 으로 따로 만들고 이 값은 대체 텍스트에만 쓴다.
 */
export function toExtensionMinutes(seconds: number): number {
  return Math.max(1, Math.ceil(seconds / 60))
}

/**
 * 연장 폭을 사람이 읽는 문구로. 실시간 현황 피드가 쓰는 규칙과 맞춘다.
 * 60초 이하는 초로, 그 위는 분으로 적는다.
 */
export function formatExtension(seconds: number): string {
  if (seconds <= 60) return `${seconds}초`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest === 0 ? `${minutes}분` : `${minutes}분 ${rest}초`
}
