import { createFileRoute } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { RouteError, RoutePending } from '@/components/route-states'
import {
  useGetResults,
  useGetRoomByShareCode,
} from '@/api/generated/경매방/경매방'
import { toRoomResult } from '@/features/live/adapt-result'
import { ClosedRoomView } from '@/features/live/components/closed-room-view'
import { useCurrentUser } from '@/lib/session'

export const Route = createFileRoute('/rooms/$shareCode/result')({
  component: RoomResultPage,
})

/**
 * 경매 결과 화면 (Figma `WEB-23` · `MOB-23`).
 *
 * 종료된 방의 도착지다. `/rooms/$shareCode` 는 진행 중인 방 전용이고, 방이
 * 닫혀 있으면 그 라우트가 여기로 돌려보낸다. 두 화면을 나눠 두는 이유는
 * 종료된 방에서 실시간 연결을 열지 않기 위해서다 — 진행 중 화면에 얹어 두면
 * 방 상태를 알기 전에 SSE 가 붙고, 서버가 그 구독을 거절해서 "실시간 연결이
 * 끊겼어요" 경고가 뜬다.
 *
 * 진행 중인 방 주소로도 열릴 수 있다. `ClosedRoomView` 가 아직 안 끝난 물품을
 * `진행 중` 으로 그린다.
 */
function RoomResultPage() {
  const { shareCode } = Route.useParams()
  const user = useCurrentUser()

  // 없는 공유 코드는 서버가 404 로 답하고 아래 에러 화면이 받는다.
  const resultsQuery = useGetResults(shareCode)
  /*
   * 판매자 조작(거래 현황)을 띄울 근거인 `isOwner` 는 결과 응답에 없어 방을 따로 읽는다.
   * 진행 중 화면에서 넘어왔으면 이미 캐시에 있어 요청이 더 나가지 않는다.
   */
  const roomQuery = useGetRoomByShareCode(shareCode)

  if (resultsQuery.isPending || roomQuery.isPending) return <RoutePending />
  if (resultsQuery.isError) {
    return (
      <RouteError
        error={resultsQuery.error}
        reset={() => void resultsQuery.refetch()}
      />
    )
  }

  const result = toRoomResult(resultsQuery.data?.data)
  if (!result) {
    return (
      <GuestShell title="경매 결과" back className="max-w-[1000px]">
        <EmptyState
          title="결과를 찾을 수 없어요"
          description="삭제되었거나 존재하지 않는 경매방입니다."
        />
      </GuestShell>
    )
  }

  /*
   * 방을 못 읽어도 결과는 보여준다. 방 조회는 판매자 조작을 띄울지만 가르므로,
   * 실패하면 구매자 화면으로 그리면 된다 — 결과 자체를 막을 이유가 없다.
   */
  return (
    <ClosedRoomView
      result={result}
      isGuest={user === null}
      isOwner={roomQuery.data?.data?.isOwner === true}
    />
  )
}
