import type { DealItemStatus } from './adapt-deal'

/**
 * 거래 상태 라벨·배지색. 거래 내역(`trades.index.tsx`)과 경매방 판매자용 거래
 * 현황(`room-deal-status.tsx`)이 같은 상태를 다른 말로 부르면 판매자가 헷갈린다.
 * 두 화면이 이 값을 공유한다.
 */
export const DEAL_STATUS_LABEL: Record<DealItemStatus, string> = {
  IN_PROGRESS: '거래 중',
  COMPLETED: '거래 완료',
  UNSOLD: '유찰',
  /*
   * 유찰과 나눠 둔다. 둘 다 거래 없이 끝났지만 유찰은 입찰이 아예 없던 것이고
   * 이쪽은 후보가 있었는데 전부 실패한 것이다. 판매자가 취할 조치가 다르다.
   */
  ALL_FAILED: '거래 불성립',
}

/** Figma 값을 그대로 쓴다 (`trades.$itemId.tsx` 의 `TONE` 과 같은 사정). */
export const DEAL_STATUS_TONE: Record<
  DealItemStatus,
  { chip: string; text: string }
> = {
  IN_PROGRESS: {
    chip: 'bg-result-progress-surface',
    text: 'text-result-progress',
  },
  COMPLETED: { chip: 'bg-result-won-surface', text: 'text-result-won' },
  UNSOLD: { chip: 'bg-result-failed-surface', text: 'text-live' },
  // 색은 유찰과 같게 두고 이름으로 구분한다 — 결과가 같은 갈래이기 때문이다.
  ALL_FAILED: { chip: 'bg-result-failed-surface', text: 'text-live' },
}
