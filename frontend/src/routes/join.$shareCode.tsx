import { AlertTriangle } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { GuestShell } from '@/components/layout/page-shell'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
import { formatWon } from '@/lib/format'
import { useCurrentUser } from '@/lib/session'

/**
 * 링크·QR 진입점.
 *
 * 비로그인도 방을 미리 볼 수 있고, 로그인한 사용자는 약관에 동의한 뒤
 * 입장한다. shareCode 로 방을 못 찾거나 이미 종료됐으면 각각 다른 안내를
 * 보여준다.
 */
export const Route = createFileRoute('/join/$shareCode')({
  component: JoinRoomPage,
})

/** 목업 판정. 실제로는 `GET /api/v1/public/auction-rooms/{shareCode}` 응답을 쓴다. */
function resolveRoom(shareCode: string) {
  if (shareCode === 'expired') return { kind: 'closed' as const }
  if (shareCode !== MOCK_ROOM_DETAIL.shareCode)
    return { kind: 'invalid' as const }
  return { kind: 'ok' as const, room: MOCK_ROOM_DETAIL }
}

function JoinRoomPage() {
  const { shareCode } = Route.useParams()
  const navigate = useNavigate()
  const user = useCurrentUser()

  const [agreed, setAgreed] = useState(false)
  const [entering, setEntering] = useState(false)

  const result = resolveRoom(shareCode)

  if (result.kind !== 'ok') {
    const invalid = result.kind === 'invalid'
    return (
      <GuestShell
        title="경매방 입장"
        className="flex min-h-[calc(100svh-8rem)] max-w-[608px] flex-col justify-center md:min-h-[calc(100svh-10rem)]"
      >
        <section className="rounded-4xl border bg-card px-6 py-14 text-center">
          <span
            aria-hidden
            className="mx-auto flex size-16 items-center justify-center rounded-full bg-live-surface"
          >
            <AlertTriangle className="size-8 text-live" />
          </span>
          <h1 className="mt-6 text-[20px] font-extrabold text-foreground md:text-[22px]">
            {invalid ? '유효하지 않은 링크예요' : '이미 종료된 경매방이에요'}
          </h1>
          <p className="mt-3 text-body font-medium text-neutral-tertiary">
            {invalid
              ? '링크가 잘못됐거나 삭제된 경매방입니다. 판매자에게 링크를 다시 받아주세요.'
              : '이 경매방은 종료됐습니다. 결과는 참여 경매방 목록에서 확인할 수 있어요.'}
          </p>
          <div className="mt-8 flex flex-col gap-2 sm:flex-row">
            <Link
              to="/"
              className="flex-1 rounded-2xl border py-3.5 text-card-title font-bold text-neutral-secondary transition-colors hover:border-border-strong"
            >
              처음으로
            </Link>
            {!invalid && (
              <Link
                to="/rooms"
                className="flex-1 rounded-2xl bg-brand-500 py-3.5 text-card-title font-bold text-white transition-opacity hover:opacity-90"
              >
                참여 경매방 보기
              </Link>
            )}
          </div>
        </section>
      </GuestShell>
    )
  }

  const { room } = result
  const isGuest = user === null

  const enter = () => {
    if (!isGuest && !agreed) return
    setEntering(true)
    // TODO: POST /api/v1/agreements 후 입장 (현재 목업)
    void navigate({ to: '/rooms/$roomId', params: { roomId: String(room.id) } })
  }

  return (
    <GuestShell
      title="경매방 입장"
      state={isGuest ? '비로그인' : user.nickname}
      // 카드 하나뿐이라 위에만 붙어 있었다. 남는 높이를 나눠 가운데로 모은다.
      className="flex min-h-[calc(100svh-8rem)] max-w-[608px] flex-col justify-center md:min-h-[calc(100svh-10rem)]"
    >
      <section className="overflow-hidden rounded-4xl border bg-card">
        {/*
         * 상단은 다른 화면의 카드와 같은 결로 둔다.
         * 그라데이션·배지·큰 제목을 얹었더니 이 화면만 튀어 보였다.
         * 썸네일 + 제목 + 판매자 한 줄, 그리고 물품은 세로 목록으로 정리한다.
         */}
        <div className="border-b px-5 py-5 md:px-7 md:py-6">
          <div className="flex items-center gap-3.5">
            <ProductThumbnail
              name={room.title}
              size={240}
              className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-fill text-neutral-muted"
            />

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <StatusBadge tone="live" dot>
                  LIVE
                </StatusBadge>
                <span className="truncate text-[11px] font-medium text-neutral-muted">
                  참여 {room.participantCount}명
                </span>
              </div>

              <h1 className="mt-1.5 truncate text-[17px] font-bold text-foreground">
                {room.title}
              </h1>
              <p className="mt-1 truncate text-[12px] font-medium text-neutral-tertiary">
                {room.sellerName}
              </p>
            </div>
          </div>
        </div>

        {/* 무엇이 올라와 있는지. 가로 스크롤 대신 목록으로 쌓는다. */}
        {room.items.length > 0 && (
          <div className="border-b px-5 py-4 md:px-7">
            <div className="flex items-baseline justify-between">
              <p className="text-[12px] font-bold text-neutral-tertiary">
                올라온 물품
              </p>
              <p className="text-[11px] font-medium text-neutral-muted">
                총 {room.items.length}개
              </p>
            </div>

            <ul className="mt-2.5 space-y-2">
              {room.items.slice(0, 3).map((item) => (
                <li key={item.id} className="flex items-center gap-3">
                  <ProductThumbnail
                    name={item.name}
                    size={160}
                    iconClassName="size-4"
                    className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-fill text-neutral-muted"
                  />
                  <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-foreground">
                    {item.name}
                  </span>
                  <span className="shrink-0 text-[13px] font-bold tabular-nums text-brand-500">
                    {formatWon(item.currentPrice)}
                  </span>
                </li>
              ))}
            </ul>

            {room.items.length > 3 && (
              <p className="mt-2.5 text-[11px] font-medium text-neutral-muted">
                외 {room.items.length - 3}개는 입장 후 볼 수 있어요
              </p>
            )}
          </div>
        )}

        <div className="px-5 pt-5 pb-6 md:px-7 md:pb-7">
          <p className="text-[13px] leading-[1.6] font-medium text-neutral-secondary">
            {room.description}
          </p>

          {isGuest ? (
            <>
              <Button
                size="cta"
                variant="brand"
                className="mt-6"
                onClick={enter}
              >
                둘러보기
              </Button>

              <p className="mt-3 text-center text-[12px] font-medium text-neutral-tertiary">
                로그인 없이도 둘러볼 수 있어요. 입찰할 때만 로그인이 필요합니다.
              </p>

              <Link
                to="/"
                search={{ redirect: `/rooms/${room.id}` }}
                className="mt-3 block text-center text-[13px] font-bold text-brand-500 hover:underline"
              >
                로그인하고 참여하기 →
              </Link>
            </>
          ) : (
            <>
              <label className="mt-6 flex cursor-pointer items-start gap-3 rounded-2xl border bg-surface-subtle p-4 transition-colors duration-150 hover:border-border-strong">
                <input
                  type="checkbox"
                  checked={agreed}
                  onChange={(event) => setAgreed(event.target.checked)}
                  className="mt-0.5 size-[18px] shrink-0 accent-brand-500"
                />
                <span className="min-w-0">
                  <span className="block text-[13px] font-bold text-foreground">
                    이용약관과 개인정보처리방침에 동의합니다
                  </span>
                  <span className="mt-1 block text-[12px] font-medium text-neutral-tertiary">
                    경매방에 처음 입장할 때 한 번만 동의하면 됩니다.
                  </span>
                  <span className="mt-2 flex items-center gap-3 text-[12px] font-bold text-brand-500">
                    <Link to="/terms/service" className="hover:underline">
                      이용약관
                    </Link>
                    <Link to="/terms/privacy" className="hover:underline">
                      개인정보처리방침
                    </Link>
                  </span>
                </span>
              </label>

              <Button
                size="cta"
                variant="brand"
                className="mt-4"
                disabled={!agreed || entering}
                onClick={enter}
              >
                {entering ? '입장하는 중…' : '동의하고 입장하기'}
              </Button>

              {/* 결제를 대행하지 않는다는 점은 입장 전에 알린다. */}
              <p className="mt-3 text-center text-[11px] leading-[1.6] font-medium text-neutral-muted">
                UpBid 는 경매 진행과 거래 연결까지 도와드려요. 결제와 배송은
                판매자·낙찰자가 직접 진행합니다.
              </p>
            </>
          )}
        </div>
      </section>
    </GuestShell>
  )
}
