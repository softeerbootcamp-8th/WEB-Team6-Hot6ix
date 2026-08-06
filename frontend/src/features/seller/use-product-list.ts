import { keepPreviousData } from '@tanstack/react-query'

import { useGetList } from '@/api/generated/상품/상품'
import type {
  GetListParams,
  ProductSummaryResponseDto,
} from '@/api/generated/model'

/**
 * 상품 목록 정렬. 서버 `sort` 파라미터 값이다 (기본 `LATEST`).
 *
 * **생성 타입에는 아직 `sort` 가 없다.** 백엔드에 파라미터를 추가했지만 `pnpm api:gen`
 * 은 백엔드를 띄워야 돌아가서, 그때까지 여기서 타입을 들고 있는다. 재생성한 뒤에는
 * 이 타입을 지우고 `GetListParams` 의 `sort` 를 그대로 쓴다.
 */
export type ProductSort = 'LATEST' | 'OLDEST'

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
export function useProductList(params: GetListParams & { sort?: ProductSort }) {
  /*
   * 생성 훅은 `GetListParams` 만 받지만, 요청은 받은 객체를 그대로 쿼리스트링으로
   * 만들고 쿼리 키에도 넣는다. 그래서 `sort` 를 실어 보내는 데는 문제가 없다.
   * 객체 리터럴로 바로 넘기면 초과 속성 검사에 걸리므로 변수로 한 번 받는다.
   */
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
