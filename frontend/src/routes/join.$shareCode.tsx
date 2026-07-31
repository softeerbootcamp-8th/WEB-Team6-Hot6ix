import { AlertTriangle, ImageIcon, LinkIcon, Users } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { GuestShell } from '@/components/layout/page-shell'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
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
      <GuestShell title="경매방 입장" className="max-w-[608px]">
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
                className="flex-1 rounded-2xl bg-primary py-3.5 text-card-title font-bold text-primary-foreground transition-opacity hover:opacity-90"
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
      className="max-w-[608px]"
    >
      <section className="rounded-4xl border bg-card p-6 md:p-8">
        <p className="flex items-center gap-1.5 text-caption font-bold text-brand-500">
          <LinkIcon aria-hidden className="size-3.5" />
          초대 링크로 입장
        </p>

        <div className="mt-5 flex gap-4">
          <span
            aria-hidden
            className="flex size-20 shrink-0 items-center justify-center rounded-2xl bg-fill text-neutral-muted"
          >
            <ImageIcon className="size-6" />
          </span>
          <div className="min-w-0">
            <StatusBadge tone="live" dot>
              LIVE
            </StatusBadge>
            <h1 className="mt-2 text-room-title font-bold text-foreground">
              {room.title}
            </h1>
            <p className="mt-1.5 text-label font-medium text-neutral-tertiary">
              {room.sellerName}
            </p>
          </div>
        </div>

        <p className="mt-5 text-body font-medium text-neutral-tertiary">
          {room.description}
        </p>

        <dl className="mt-6 grid grid-cols-2 gap-3">
          <div className="rounded-2xl bg-surface-subtle p-4">
            <dt className="text-caption font-normal text-neutral-muted">
              물품
            </dt>
            <dd className="mt-1.5 text-body-strong font-semibold text-foreground">
              {room.items.length}개
            </dd>
          </div>
          <div className="rounded-2xl bg-surface-subtle p-4">
            <dt className="flex items-center gap-1.5 text-caption font-normal text-neutral-muted">
              <Users aria-hidden className="size-3.5" />
              참여
            </dt>
            <dd className="mt-1.5 text-body-strong font-semibold text-foreground">
              {room.participantCount}명
            </dd>
          </div>
        </dl>

        {isGuest ? (
          <>
            <Button size="cta" className="mt-7" onClick={enter}>
              둘러보기
            </Button>
            <p className="mt-3 text-center text-caption font-normal text-neutral-muted">
              입찰하려면 로그인이 필요해요. 로그인 후 이 방으로 돌아옵니다.
            </p>
            <Link
              to="/"
              search={{ redirect: `/rooms/${room.id}` }}
              className="mt-3 block text-center text-label font-bold text-brand-500 hover:underline"
            >
              로그인하고 참여하기
            </Link>
          </>
        ) : (
          <>
            <label className="mt-7 flex items-start gap-3 rounded-2xl bg-surface-subtle p-4">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(event) => setAgreed(event.target.checked)}
                className="mt-0.5 size-4 shrink-0 accent-brand-500"
              />
              <span className="text-label font-medium text-neutral-secondary">
                이용약관과 개인정보처리방침에 동의합니다
                <span className="mt-1 block text-caption font-normal text-neutral-muted">
                  경매방에 처음 입장할 때 한 번만 동의하면 됩니다.
                </span>
              </span>
            </label>

            <Button
              size="cta"
              className="mt-4"
              disabled={!agreed || entering}
              onClick={enter}
            >
              {entering ? '입장하는 중…' : '동의하고 입장하기'}
            </Button>
          </>
        )}
      </section>
    </GuestShell>
  )
}
