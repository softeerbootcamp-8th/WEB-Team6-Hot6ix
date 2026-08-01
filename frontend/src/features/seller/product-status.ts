import type { ProductStatus } from '@/mocks/types'

/**
 * 상품의 경매 결과 배지.
 *
 * Figma `WEB-02 · 판매자 · 판매자 정보 진입` / `WEB-05 · 판매자 · 상품 관리`의
 * "경매 결과" 열에서 값을 그대로 옮겼다. 상품은 한 번의 경매에만 쓰이므로
 * 상품 상태가 곧 경매 결과다.
 */
export const PRODUCT_RESULT: Record<
  ProductStatus,
  { label: string; className: string }
> = {
  DRAFT: {
    label: '미진행',
    className: 'bg-result-idle-surface text-result-idle',
  },
  IN_AUCTION: { label: '경매 중', className: 'bg-brand-50 text-brand-500' },
  SOLD: { label: '낙찰', className: 'bg-result-won-surface text-result-won' },
  UNSOLD: {
    label: '유찰',
    className: 'bg-result-failed-surface text-result-failed',
  },
}
