import { keepPreviousData } from '@tanstack/react-query'

import { useGetList } from '@/api/generated/상품/상품'
import type {
  GetListParams,
  ProductSummaryResponseDto,
} from '@/api/generated/model'

/**
 * 판매자 본인의 상품 목록 한 페이지.
 *
 * 서버가 offset 페이지네이션이라 `page` 를 넘기면 그 페이지로 바로 간다. 전체 개수와
 * 전체 페이지 수가 같은 응답에서 오므로 "총 N개"와 페이지 버튼을 이 훅 하나로 그린다.
 *
 * `keepPreviousData` 는 페이지를 넘기는 동안 목록이 빈 화면으로 깜빡이지 않게 한다.
 * 새 페이지가 도착할 때까지 이전 페이지가 남아 있고, 그 사이인지는 `isPlaceholderData`
 * 로 알 수 있다.
 */
export function useProductList(params: GetListParams) {
  const query = useGetList(params, {
    query: { placeholderData: keepPreviousData },
  })

  const page = query.data?.data

  const products: ProductSummaryResponseDto[] = page?.content ?? []

  return {
    ...query,
    products,
    /** 필터를 적용한 뒤의 전체 개수. 페이지 크기와 무관하다. */
    totalElements: page?.totalElements ?? 0,
    totalPages: page?.totalPages ?? 0,
  }
}
