import { useQuery } from '@tanstack/react-query'

import {
  getGetMyRoomCountsQueryKey,
  getMyRoomCounts,
} from '@/api/generated/경매방/경매방'
import type { GetMyRoomCountsParams } from '@/api/generated/model'

/**
 * 필터 바의 탭 숫자.
 *
 * 목록 응답으로는 셀 수 없다. 한 쪽이 20개라 방이 50개면 "전체 20"이 되고, 상태 탭을
 * 누르면 서버가 그 상태만 주니 다른 탭이 0으로 찍힌다.
 *
 * 서버가 상태를 안 받으므로 탭을 바꿔도 이 값은 그대로다. 역할·검색어를 바꿀 때만
 * 다시 받는다 — 쿼리 키에 status 가 없는 이유다.
 */
export function useMyRoomCounts(params: GetMyRoomCountsParams) {
  const query = useQuery({
    queryKey: getGetMyRoomCountsQueryKey(params),
    queryFn: ({ signal }) => getMyRoomCounts(params, undefined, signal),
  })

  return { ...query, counts: query.data?.data }
}
