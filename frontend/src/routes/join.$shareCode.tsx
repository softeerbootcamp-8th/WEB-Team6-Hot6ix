import { AlertTriangle, LinkIcon } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { GuestShell } from '@/components/layout/page-shell'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
import { cn } from '@/lib/utils'
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
         * 방 정보를 브랜드면 위에 얹어 "이 방에 들어간다"를 먼저 보여준다.
         * 예전에는 흰 카드 안에 작은 썸네일과 글자만 있어 무엇을 확인하고
         * 동의하는지가 눈에 들어오지 않았다.
         */}
        <div className="bg-gradient-to-b from-brand-50 to-card px-6 pt-7 pb-6 md:px-8">
          <span className="inline-flex h-7 items-center gap-1.5 rounded-full border border-brand-200 bg-card px-3 text-[11px] font-extrabold tracking-[0.04em] text-brand-600">
            <LinkIcon aria-hidden className="size-3" />
            초대 링크로 입장
          </span>

          <div className="mt-4 flex items-center gap-4">
            <ProductThumbnail
              name={room.title}
              size={280}
              className="flex size-[96px] shrink-0 items-center justify-center rounded-2xl border border-brand-200 bg-card text-neutral-muted shadow-sm"
            />

            <div className="min-w-0">
              <StatusBadge tone="live" dot>
                LIVE
              </StatusBadge>
              <h1 className="mt-2 truncate text-[22px] font-extrabold text-foreground">
                {room.title}
              </h1>
              <p className="mt-1.5 truncate text-[13px] font-medium text-neutral-tertiary">
                {room.sellerName} · 참여 {room.participantCount}명
              </p>
            </div>
          </div>

          {/* 무엇이 올라와 있는지 먼저 보여준다. 링크만 받고 들어온 사람에게 제일 궁금한 것. */}
          {room.items.length > 0 && (
            <ul className="mt-5 flex gap-2 overflow-x-auto pb-1">
              {room.items.slice(0, 4).map((item) => (
                <li
                  key={item.id}
                  className="flex w-[120px] shrink-0 flex-col rounded-2xl border bg-card p-2"
                >
                  <ProductThumbnail
                    name={item.name}
                    size={200}
                    iconClassName="size-4"
                    className="flex h-[68px] w-full items-center justify-center rounded-xl bg-fill text-neutral-muted"
                  />
                  <span className="mt-2 truncate text-[12px] font-bold text-foreground">
                    {item.name}
                  </span>
                  <span className="mt-0.5 text-[11px] font-semibold tabular-nums text-brand-500">
                    {formatWon(item.currentPrice)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="px-6 pt-6 pb-7 md:px-8 md:pb-8">
          <p className="text-[14px] leading-[1.6] font-medium text-neutral-secondary">
            {room.description}
          </p>

          <dl className="mt-5 grid grid-cols-3 gap-2">
            {[
              {
                label: '물품',
                value: `${room.items.length}개`,
                bg: 'bg-brand-50',
                color: 'text-brand-500',
              },
              {
                label: '참여',
                value: `${room.participantCount}명`,
                bg: 'bg-result-idle-surface',
                color: 'text-result-idle',
              },
              {
                label: '상태',
                value: '진행 중',
                bg: 'bg-result-failed-surface',
                color: 'text-live',
              },
            ].map((stat) => (
              <div
                key={stat.label}
                className={cn('rounded-2xl px-3 py-3 text-center', stat.bg)}
              >
                <dd
                  className={cn(
                    'text-[16px] font-extrabold tabular-nums',
                    stat.color,
                  )}
                >
                  {stat.value}
                </dd>
                <dt className="mt-1 text-[11px] font-semibold text-neutral-secondary">
                  {stat.label}
                </dt>
              </div>
            ))}
          </dl>

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
