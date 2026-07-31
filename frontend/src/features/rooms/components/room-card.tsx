import { Link } from '@tanstack/react-router'
import { ImageIcon } from 'lucide-react'

import { formatDate } from '@/lib/format'
import type { AuctionRoomSummary } from '@/mocks/types'

/**
 * 참여 경매방 목록 카드 (Figma `WEB-03 · 구매자 · 참여 경매방 목록`).
 *
 * 글자 크기는 Figma 값을 그대로 쓴다.
 * 제목 17/700 · 판매자 13/500 · 배지 11/800 · 역할 태그 12/700 · CTA 13/700
 */
export function RoomCard({ room }: { room: AuctionRoomSummary }) {
  const isLive = room.status === 'LIVE'

  return (
    <li className="rounded-2xl border bg-card p-4">
      <div className="flex gap-4">
        <span
          aria-hidden
          className="flex size-[124px] shrink-0 flex-col items-center justify-center gap-1.5 rounded-xl bg-border-strong text-white sm:size-[136px]"
        >
          <ImageIcon className="size-6" />
          <span className="text-[11px] font-medium">상품 이미지</span>
        </span>

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
            ) : (
              <span className="flex h-[22px] items-center rounded-md bg-fill px-2 text-[11px] font-bold text-neutral-tertiary">
                종료
              </span>
            )}

            <span
              className={
                room.role === 'SELLER'
                  ? 'ml-auto text-[12px] font-bold text-brand-500'
                  : 'ml-auto text-[12px] font-bold text-neutral-tertiary'
              }
            >
              {room.role === 'SELLER' ? '판매자 운영' : '구매자 참여'}
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
            <p className="text-[12px] font-medium text-neutral-tertiary">
              {isLive
                ? `${room.participantCount}명 참여 중`
                : `참여 ${room.participantCount}명`}
            </p>

            {isLive ? (
              <Link
                to="/rooms/$roomId"
                params={{ roomId: String(room.id) }}
                className="ml-auto flex h-9 items-center rounded-[10px] bg-primary px-5 text-[13px] font-bold text-primary-foreground transition-opacity hover:opacity-90"
              >
                입장하기
              </Link>
            ) : (
              // 종료된 방도 같은 경로로 들어간다. 방 상태를 보고 종료 화면을 그린다.
              <Link
                to="/rooms/$roomId"
                params={{ roomId: String(room.id) }}
                className="ease-soft ml-auto flex h-9 items-center rounded-[10px] border px-4 text-[13px] font-semibold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
              >
                종료 {room.closedAt ? formatDate(room.closedAt) : ''}
              </Link>
            )}
          </div>
        </div>
      </div>
    </li>
  )
}
