import { Link } from '@tanstack/react-router'

import { ProductThumbnail } from '@/components/product-thumbnail'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import type { RoomRole, RoomStatus } from '@/mocks/types'

/**
 * 카드가 그리는 데 필요한 값만 추린 모양.
 *
 * 목업(`AuctionRoomSummary`)과 서버 목록 응답이 필드가 달라 둘 다 이 모양으로
 * 맞춰 넘긴다. `participantCount` 는 참여자를 기록하는 코드가 아직 없어(이슈 #117)
 * 서버 응답에서 늘 비고, `closedAt` 은 목록 응답에 아예 없다 — 둘 다 없으면
 * 해당 줄을 그리지 않는다.
 */
export interface RoomCardData {
  id: number
  title: string
  sellerName: string
  status: RoomStatus
  role: RoomRole
  itemCount: number
  participantCount?: number | null
  closedAt?: string | null
  imageUrl?: string
}

/**
 * 참여 경매방 목록 카드 (Figma `WEB-03 · 구매자 · 참여 경매방 목록`).
 *
 * 글자 크기는 Figma 값을 그대로 쓴다.
 * 제목 17/700 · 판매자 13/500 · 배지 11/800 · 역할 태그 12/700 · CTA 13/700
 */
export function RoomCard({ room }: { room: RoomCardData }) {
  const isLive = room.status === 'LIVE'
  // 아직 시작 전인 방을 종료로 묶으면 판매자가 방을 못 찾는다.
  const isClosed = room.status === 'CLOSED'

  return (
    <li className="rounded-2xl border bg-card p-4">
      <div className="flex gap-4">
        <ProductThumbnail
          src={room.imageUrl}
          className="flex size-[124px] shrink-0 flex-col items-center justify-center gap-1.5 rounded-xl bg-fill text-neutral-muted sm:size-[136px]"
        />

        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex items-center gap-2">
            {isLive ? (
              <span className="flex h-[22px] items-center gap-1 rounded-md bg-live px-2 text-[11px] font-extrabold text-white">
                <span
                  aria-hidden
                  className="size-1.5 rounded-full bg-current"
                />
                LIVE
              </span>
            ) : isClosed ? (
              <span className="flex h-[22px] items-center rounded-md bg-fill px-2 text-[11px] font-bold text-neutral-tertiary">
                종료
              </span>
            ) : (
              <span className="flex h-[22px] items-center rounded-md bg-notice-surface px-2 text-[11px] font-bold text-notice">
                준비 중
              </span>
            )}

            {/* 역할도 배지로. 다른 화면(거래 내역·거래 상세)과 모양을 맞춘다. */}
            <span
              className={cn(
                'ml-auto flex h-[22px] shrink-0 items-center rounded-md px-2 text-[11px] font-bold',
                room.role === 'SELLER'
                  ? 'bg-result-won-surface text-result-won'
                  : 'bg-brand-50 text-brand-500',
              )}
            >
              {room.role === 'SELLER' ? '판매자' : '구매자'}
            </span>
          </div>

          <h3 className="mt-2.5 truncate text-[17px] font-bold text-foreground">
            {room.title}
          </h3>
          <p className="mt-1.5 text-[13px] font-medium text-neutral-tertiary">
            {room.sellerName}
          </p>

          <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-2 pt-4">
            <p className="text-[12px] font-semibold text-neutral-secondary">
              물품 {room.itemCount}
            </p>
            {/* 참여자 수를 모르면 "0명"이라고 단정하지 않고 줄을 뺀다. */}
            {room.participantCount != null && (
              <p className="text-[12px] font-medium text-neutral-tertiary">
                {isLive
                  ? `${room.participantCount}명 참여 중`
                  : `참여 ${room.participantCount}명`}
              </p>
            )}

            {isLive ? (
              <Link
                to="/rooms/$roomId"
                params={{ roomId: String(room.id) }}
                className="ml-auto flex h-9 items-center rounded-[10px] bg-brand-500 px-5 text-[13px] font-bold text-white transition-opacity hover:opacity-90"
              >
                입장하기
              </Link>
            ) : (
              // 어느 상태든 같은 경로로 들어간다. 방 상태를 보고 화면을 나눈다.
              <Link
                to="/rooms/$roomId"
                params={{ roomId: String(room.id) }}
                className="ease-soft ml-auto flex h-9 items-center rounded-[10px] border px-4 text-[13px] font-semibold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
              >
                {isClosed
                  ? `종료 ${room.closedAt ? formatDate(room.closedAt) : ''}`
                  : '방 준비하기'}
              </Link>
            )}
          </div>
        </div>
      </div>
    </li>
  )
}
