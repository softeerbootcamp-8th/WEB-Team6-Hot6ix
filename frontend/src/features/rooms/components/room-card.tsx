import { Link } from '@tanstack/react-router'

import { ProductThumbnail } from '@/components/product-thumbnail'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import type { RoomRole, RoomStatus } from '@/types/domain'

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
  /** 방 화면으로 들어가는 공개 식별자. 숫자 ID 로는 방을 열 수 없다. */
  shareCode: string
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
    /*
     * **`min-w-0` 이 없으면 카드가 화면 밖으로 넘어간다.** 목록이 `grid` 인데
     * 항목의 기본 `min-width` 는 `auto` 라 내용의 min-content 폭까지 늘어난다.
     * 제목에 걸린 `truncate` 가 `nowrap` 이라 방 이름이 길면 min-content 가 그
     * 길이만큼 커지고, 카드가 넓어지면서 오른쪽 끝의 입장 버튼이 화면 밖으로
     * 밀려 아예 안 보였다 (`UI-RULES.md` 4장의 같은 지뢰).
     */
    <li className="min-w-0 rounded-2xl border bg-card p-4">
      {/*
        모바일은 카드 폭이 좁아 썸네일을 한 단계 줄이고 사이 여백을 넓힌다.
        124 + 16 이면 오른쪽 글자 칸이 눌려 제목이 금방 잘렸다.
      */}
      <div className="flex gap-5 sm:gap-4">
        <ProductThumbnail
          src={room.imageUrl}
          className="flex size-[88px] shrink-0 flex-col items-center justify-center gap-1.5 rounded-xl bg-fill text-neutral-muted sm:size-[136px]"
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

          {/*
            길면 자른다. 방 이름은 서버가 100자까지 받는데, 카드 폭은 정해져
            있어서 통째로 보여줄 방법이 없다.
          */}
          <h3 className="mt-2.5 truncate text-[15px] font-bold text-foreground sm:text-[17px]">
            {room.title}
          </h3>
          <p className="mt-1.5 text-[13px] font-medium text-neutral-tertiary">
            {room.sellerName}
          </p>

          {/*
            **버튼은 절대 줄어들지 않는다.** `shrink-0` 이 없으면 공간이 모자랄 때
            flex 가 버튼부터 짓눌러서, 방 이름이 긴 카드에서는 "입장하기" 가
            폭 0 까지 밀려 아예 안 보였다. 좁으면 글자 쪽이 줄어야 한다.
          */}
          <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-2 pt-4">
            <p className="shrink-0 text-[12px] font-semibold text-neutral-secondary">
              물품 {room.itemCount}
            </p>
            {/* 참여자 수를 모르면 "0명"이라고 단정하지 않고 줄을 뺀다. */}
            {room.participantCount != null && (
              <p className="min-w-0 truncate text-[12px] font-medium text-neutral-tertiary">
                {isLive
                  ? `${room.participantCount}명 참여 중`
                  : `참여 ${room.participantCount}명`}
              </p>
            )}

            {isLive ? (
              <Link
                to="/rooms/$shareCode"
                params={{ shareCode: room.shareCode }}
                className="ml-auto flex h-9 shrink-0 items-center rounded-[10px] bg-brand-500 px-5 text-[13px] font-bold whitespace-nowrap text-white transition-opacity hover:opacity-90"
              >
                입장하기
              </Link>
            ) : (
              // 어느 상태든 같은 경로로 들어간다. 방 상태를 보고 화면을 나눈다.
              <Link
                to="/rooms/$shareCode"
                params={{ shareCode: room.shareCode }}
                className="ease-soft ml-auto flex h-9 shrink-0 items-center rounded-[10px] border px-4 text-[13px] font-semibold whitespace-nowrap text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
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
