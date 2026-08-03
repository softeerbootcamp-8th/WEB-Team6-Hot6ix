import { useInfiniteQuery } from '@tanstack/react-query'

import { getGetListQueryKey, getList } from '@/api/generated/상품/상품'
import type {
  GetListParams,
  ProductSummaryResponseDto,
} from '@/api/generated/model'

/**
 * 판매자 본인의 상품 목록.
 *
 * 서버가 커서 페이지네이션이라 전체 개수도, 임의 페이지로 건너뛰는 길도 없다.
 * 그래서 번호 페이지가 아니라 "더 보기"로 이어 붙인다.
 *
 * orval 설정에 무한 쿼리 생성이 꺼져 있어(`useInfinite: false`) 생성된 요청 함수와
 * 쿼리 키만 가져다 `useInfiniteQuery` 를 직접 조립한다. 요청 자체는 그대로
 * `custom-instance` 를 통과한다.
 */
export function useProductList(params: GetListParams) {
  const query = useInfiniteQuery({
    queryKey: getGetListQueryKey(params),
    queryFn: ({ pageParam, signal }) =>
      getList({ ...params, cursor: pageParam }, undefined, signal),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.data?.hasNext ? lastPage.data.nextCursor : undefined,
  })

  const products: ProductSummaryResponseDto[] =
    query.data?.pages.flatMap((page) => page.data?.content ?? []) ?? []

  return { ...query, products }
}
