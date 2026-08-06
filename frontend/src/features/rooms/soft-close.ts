/**
 * 경매방의 Soft Close 설정(마감 임박 기준, 연장 시간) 범위.
 *
 * **서버가 받아주는 범위와 같은 값이어야 한다.** `AuctionRoomCreateRequestDto` 가
 * 두 값을 `@Min(60) @Max(3600)` 초로 막고 있어서, 화면이 더 넓게 받으면 요청을
 * 보낸 뒤에야 400 이 돌아온다. 실제로 상한이 없어서 61분을 그대로 보내던 것이
 * 이슈 #220 이다.
 *
 * 생성 화면(`seller.rooms.new.tsx`)과 설정 수정 모달(`room-settings-modal.tsx`)이
 * 같은 값을 써야 해서 여기 한 곳에 둔다.
 */
export const MIN_SOFT_CLOSE_MINUTES = 1
export const MAX_SOFT_CLOSE_MINUTES = 60

/**
 * 범위를 벗어났으면 안내 문구를, 아니면 `undefined` 를 준다.
 *
 * `null` 이 아니라 `undefined` 인 것은 `Field` 의 `error` 가 그 타입이고,
 * 안쪽에서 `error !== undefined` 로 `aria-invalid` 를 정하기 때문이다.
 */
export function softCloseMinutesError(minutes: number): string | undefined {
  if (minutes < MIN_SOFT_CLOSE_MINUTES) {
    return `${MIN_SOFT_CLOSE_MINUTES}분 이상으로 설정해주세요.`
  }
  if (minutes > MAX_SOFT_CLOSE_MINUTES) {
    return `최대 ${MAX_SOFT_CLOSE_MINUTES}분까지 설정할 수 있어요.`
  }
  return undefined
}
