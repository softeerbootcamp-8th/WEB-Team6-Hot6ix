import { useInfiniteQuery } from '@tanstack/react-query'

import {
  getGetMyRoomsQueryKey,
  getMyRooms,
} from '@/api/generated/경매방/경매방'
import type {
  AuctionRoomListItemResponseDto,
  GetMyRoomsParams,
} from '@/api/generated/model'

/**
 * 내 경매방 목록. 내가 만든 방과 참여한 방이 같이 온다.
 *
 * 서버가 커서 페이지네이션이라 전체 개수도, 임의 페이지로 건너뛰는 길도 없다.
 * 그래서 번호 페이지가 아니라 "더 보기"로 이어 붙인다. 탭 숫자는 이 응답으로 못 세고
 * `use-my-room-counts.ts` 가 따로 받는다.
 *
 * 상태·역할·검색어를 서버 파라미터로 넘기는 게 중요하다. 받아온 페이지 안에서 거르면
 * 아직 안 불러온 방은 검색에 걸리지 않는다.
 *
 * orval 설정에 무한 쿼리 생성이 꺼져 있어(`useInfinite: false`) 생성된 요청 함수와
 * 쿼리 키만 가져다 `useInfiniteQuery` 를 직접 조립한다 — `use-product-list.ts` 와 같다.
 */
export function useMyRoomList(params: GetMyRoomsParams) {
  const query = useInfiniteQuery({
    queryKey: getGetMyRoomsQueryKey(params),
    queryFn: ({ pageParam, signal }) =>
      getMyRooms({ ...params, cursor: pageParam }, undefined, signal),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.data?.hasNext ? lastPage.data.nextCursor : undefined,
  })

  const rooms: AuctionRoomListItemResponseDto[] =
    query.data?.pages.flatMap((page) => page.data?.content ?? []) ?? []

  return { ...query, rooms }
}
