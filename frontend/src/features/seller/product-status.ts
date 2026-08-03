import type { ProductSummaryResponseDtoStatus } from '@/api/generated/model'

/**
 * 상품의 경매 상태 배지.
 *
 * 서버가 계산해 주는 파생값(`ProductListingStatus`)을 그대로 표시한다. 프론트가
 * 따로 관리하지 않는다. 서버는 낙찰과 유찰을 `ENDED` 하나로 합치므로 Figma 의
 * "경매 결과" 열은 "경매 상태"가 된다 — 낙찰가·유찰 여부는 거래 화면에서 본다.
 */
export const PRODUCT_STATUS: Record<
  ProductSummaryResponseDtoStatus,
  { label: string; className: string }
> = {
  UNREGISTERED: {
    label: '경매 미등록',
    className: 'bg-result-idle-surface text-result-idle',
  },
  READY: { label: '경매 대기', className: 'bg-brand-50 text-brand-500' },
  IN_PROGRESS: { label: '경매 중', className: 'bg-live-surface text-live' },
  ENDED: { label: '경매 종료', className: 'bg-fill text-neutral-muted' },
}

/**
 * 수정 가능 여부. 서버는 연결된 물품이 모두 시작 전(`READY`)이면 수정을 허용한다.
 * 어기면 `409 PRODUCT_AUCTION_ALREADY_STARTED`.
 */
export function canEditProduct(
  status: ProductSummaryResponseDtoStatus | undefined,
) {
  return status === 'UNREGISTERED' || status === 'READY'
}

/**
 * 삭제 가능 여부. 삭제는 수정보다 엄격해서 경매방에 담기기만 해도 막힌다 —
 * 상품만 지우면 물품 행이 남아 삭제된 상품이 경매방에 계속 노출되기 때문이다.
 * 어기면 `409 PRODUCT_IN_AUCTION`.
 */
export function canDeleteProduct(
  status: ProductSummaryResponseDtoStatus | undefined,
) {
  return status === 'UNREGISTERED'
}
