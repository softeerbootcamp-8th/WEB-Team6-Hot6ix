import { Link } from '@tanstack/react-router'
import { Share2, Users } from 'lucide-react'
import type { ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { ConnectionPill } from '@/features/live/components/connection-banner'
import type { RealtimeStatus } from '@/features/live/use-realtime-status'
import type { AuctionRoomDetail } from '@/mocks/types'

/**
 * 라이브 화면 공통 골격 (Figma `WEB-09` / `WEB-13`).
 *
 * **데스크톱(lg 이상)**: 화면이 통째로 스크롤되지 않는다. 상단바(64) +
 * 방 헤더(68) 아래 남은 높이를 세 열이 나눠 갖고 각 열이 자기 안에서만
 * 스크롤한다. 열 폭은 Figma 기준 332 / 552 / 332, 간격 12.
 *
 * **모바일·태블릿**: 세 열을 세로로 쌓고 페이지 스크롤로 바꾼다.
 * (Figma `MOB-04` 전용 구성은 아직 반영하지 않았다.)
 */
export function LiveShell({
  room,
  status,
  isGuest,
  onShare,
  left,
  leftLabel,
  center,
  centerLabel,
  right,
  rightLabel,
}: {
  room: AuctionRoomDetail
  status: RealtimeStatus
  isGuest: boolean
  /** 방 헤더의 공유 버튼. 오른쪽 열에 공유 패널을 띄운다. */
  onShare?: () => void
  leftLabel: ReactNode
  left: ReactNode
  centerLabel: ReactNode
  center: ReactNode
  /** 물품 상세처럼 가운데가 오른쪽까지 차지하면 생략한다. */
  rightLabel?: ReactNode
  right?: ReactNode
}) {
  return (
    <div className="flex min-h-svh flex-col bg-background lg:h-svh lg:min-h-0 lg:overflow-hidden">
      {isGuest ? <GuestHeader /> : <AppHeader />}

      {/* 방 헤더 */}
      <div className="shrink-0 border-b bg-card">
        <div className="mx-auto flex min-h-[68px] max-w-[1280px] flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 md:px-7">
          <span className="flex h-6 items-center rounded-full bg-live px-3 text-[11px] font-extrabold text-white">
            LIVE
          </span>

          <h1 className="text-[17px] font-bold text-foreground">
            {room.title}
          </h1>

          <p className="flex items-center gap-1.5 text-[13px] font-medium text-neutral-tertiary">
            <Users aria-hidden className="size-[15px]" />
            {room.participantCount}명 참여 중
          </p>

          <div className="ml-auto flex items-center gap-3">
            <ConnectionPill status={status} />
            <button
              type="button"
              onClick={onShare}
              aria-label="경매방 공유"
              className="ease-soft flex h-8 w-10 items-center justify-center rounded-[10px] border border-border-strong bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
            >
              <Share2 aria-hidden className="size-[18px]" />
            </button>
          </div>
        </div>
      </div>

      {/* 본문 */}
      <div className="lg:min-h-0 lg:flex-1">
        <div className="mx-auto flex max-w-[1280px] flex-col gap-5 px-5 pt-[18px] pb-8 lg:h-full lg:flex-row lg:gap-3 lg:pb-[34px]">
          <section className="flex flex-col lg:min-h-0 lg:w-[332px] lg:shrink-0">
            <h2 className="pb-2.5 text-[13px] font-bold text-neutral-tertiary lg:px-2">
              {leftLabel}
            </h2>
            {/* 내용이 짧으면 박스도 같이 줄고, 길어지면 화면 높이에서 멈춘다 */}
            <div className="flex flex-col rounded-2xl border p-2 lg:min-h-0 lg:max-h-full">
              {left}
            </div>
          </section>

          <section className="flex min-w-0 flex-col lg:min-h-0 lg:flex-1 lg:self-stretch">
            <div className="flex items-center pb-2.5 text-[13px] font-bold text-neutral-tertiary">
              {centerLabel}
            </div>
            <div className="flex flex-col lg:min-h-0 lg:flex-1">{center}</div>
          </section>

          {right && (
            <section className="flex flex-col lg:min-h-0 lg:w-[332px] lg:shrink-0">
              <h2 className="pb-2.5 text-[13px] font-bold text-neutral-tertiary lg:px-2">
                {rightLabel}
              </h2>
              <div className="flex flex-col rounded-2xl border p-2 lg:min-h-0 lg:max-h-full">
                {right}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  )
}

/** 게스트에게 보여주는 로그인 유도 줄. */
export function GuestNotice({ redirectTo }: { redirectTo: string }) {
  return (
    <div className="mb-3 flex shrink-0 flex-wrap items-center gap-3 rounded-xl bg-brand-50 px-4 py-2.5">
      <p className="text-[13px] font-medium text-brand-600">
        둘러보는 건 로그인 없이도 가능해요. 입찰하려면 로그인이 필요합니다.
      </p>
      <Link
        to="/"
        search={{ redirect: redirectTo }}
        className="ml-auto flex h-8 items-center rounded-lg bg-primary px-3.5 text-[13px] font-bold text-primary-foreground transition-opacity hover:opacity-90"
      >
        로그인
      </Link>
    </div>
  )
}
