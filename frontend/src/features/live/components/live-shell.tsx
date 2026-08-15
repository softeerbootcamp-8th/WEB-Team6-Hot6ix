import { Link } from '@tanstack/react-router'
import { Settings, Share2, Square, Users } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { RoomRuleChips } from '@/features/live/components/room-rule-chips'
import { cn } from '@/lib/utils'
import type { AuctionRoomDetail } from '@/types/domain'

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
  isGuest,
  participantCount,
  onShare,
  onOpenSettings,
  onCloseRoom,
  left,
  headerActions,
  leftLabel,
  center,
  centerLabel,
  right,
  rightLabel,
  overlay,
}: {
  room: AuctionRoomDetail
  isGuest: boolean
  /**
   * SSE 로 받은 실시간 참여자 수. 아직 못 받았으면 방 정보의 값을 쓴다.
   */
  participantCount?: number | null
  /** 방 헤더의 공유 버튼. 오른쪽 열에 공유 패널을 띄운다. */
  onShare?: () => void
  /** 공유 버튼 왼쪽에 들어가는 보조 조작(개발용 시점 전환 등) */
  headerActions?: ReactNode
  /** 판매자만 받는다. 방 설정 수정 모달을 연다. */
  onOpenSettings?: () => void
  /** 판매자만 받는다. 방 전체를 끝낸다. */
  onCloseRoom?: () => void
  leftLabel: ReactNode
  left: ReactNode
  centerLabel: ReactNode
  center: ReactNode
  /** 물품 상세처럼 가운데가 오른쪽까지 차지하면 생략한다. */
  rightLabel?: ReactNode
  right?: ReactNode
  /**
   * 가운데·오른쪽 열을 덮는 층. 물품 상세처럼 "지금 보던 화면 위에"
   * 얹혀야 하는 것에 쓴다. 왼쪽 물품 목록은 그대로 보인다.
   */
  overlay?: ReactNode
}) {
  /*
   * 라이브 화면이 떠 있는 동안에는 문서 자체를 잠근다.
   * 높이를 100dvh 로 맞춰도, 바깥에 무엇 하나라도 더 그려지면 페이지가
   * 스크롤된다. 여기서 확실히 막고 화면을 떠날 때 되돌린다.
   */
  useEffect(() => {
    const root = document.documentElement
    const previousRoot = root.style.overflow
    const previousBody = document.body.style.overflow

    root.style.overflow = 'hidden'
    document.body.style.overflow = 'hidden'

    return () => {
      root.style.overflow = previousRoot
      document.body.style.overflow = previousBody
    }
  }, [])

  return (
    // 라이브 화면은 페이지 자체가 스크롤되지 않는다. 안쪽 열만 스크롤한다.
    <div className="flex h-[100dvh] flex-col overflow-hidden bg-background">
      {isGuest ? <GuestHeader /> : <AppHeader />}

      {/* 방 헤더 */}
      <div className="shrink-0 border-b bg-card">
        <div className="mx-auto flex min-h-[68px] max-w-[1280px] flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 md:px-7">
          {/*
            배지를 LIVE 로 못 박아 두면 물품 상세 라우트처럼 종료된 방에도
            이 골격을 그대로 쓰는 화면에서 "LIVE" 가 뜬다. 방 상태를 따른다.
          */}
          <span
            className={cn(
              'flex h-6 items-center rounded-full px-3 text-[11px] font-extrabold',
              room.status === 'CLOSED'
                ? 'bg-fill text-neutral-tertiary'
                : room.status === 'READY'
                  ? 'bg-notice-surface text-notice'
                  : 'bg-live text-white',
            )}
          >
            {room.status === 'CLOSED'
              ? '종료'
              : room.status === 'READY'
                ? '준비 중'
                : 'LIVE'}
          </span>

          <h1 className="text-[17px] font-bold text-foreground">
            {room.title}
          </h1>

          {/* 누가 파는 방인지. 링크만 받고 들어온 사람에게는 이게 첫 단서다. */}
          {room.sellerName && (
            <p className="min-w-0 truncate text-[13px] font-medium text-neutral-secondary">
              {room.sellerName}
            </p>
          )}

          <p className="flex items-center gap-1.5 text-[13px] font-medium text-neutral-tertiary">
            <Users aria-hidden className="size-[15px]" />
            {participantCount ?? room.participantCount}명 참여 중
          </p>

          <RoomRuleChips room={room} />

          <div className="ml-auto flex items-center gap-2">
            {headerActions}

            <button
              type="button"
              onClick={onShare}
              className="ease-soft flex h-8 items-center gap-1.5 rounded-[10px] border border-border-strong bg-card px-3 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
            >
              <Share2 aria-hidden className="size-[15px]" />
              공유
            </button>

            {/* 방을 만든 사람만 보인다. */}
            {onOpenSettings && (
              <button
                type="button"
                onClick={onOpenSettings}
                className="ease-soft flex h-8 items-center gap-1.5 rounded-[10px] border border-border-strong bg-card px-3 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
              >
                <Settings aria-hidden className="size-[15px]" />
                설정
              </button>
            )}

            {/* 방을 만든 사람만 보인다. 되돌릴 수 없어 확인을 한 번 받는다. */}
            {onCloseRoom && (
              <button
                type="button"
                onClick={onCloseRoom}
                className="ease-soft flex h-8 items-center gap-1.5 rounded-[10px] border border-live/40 bg-card px-3 text-[12px] font-bold text-live transition-all duration-150 hover:bg-live-surface active:scale-95"
              >
                <Square aria-hidden className="size-[13px]" />
                경매방 종료
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 본문 — 남은 높이를 채우고, 넘치면 이 안에서만 스크롤한다. */}
      <div className="min-h-0 flex-1 overflow-y-auto lg:overflow-hidden">
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

          {/*
           * 가운데 + 오른쪽을 한 덩어리로 묶는다. 물품 상세 같은 층이
           * 이 영역만 덮고 왼쪽 목록은 계속 보이게 하기 위해서다.
           */}
          <div className="relative flex min-w-0 flex-col gap-5 lg:min-h-0 lg:flex-1 lg:flex-row lg:gap-3">
            <section className="flex min-w-0 flex-col lg:min-h-0 lg:flex-1 lg:self-stretch">
              <div className="flex items-center pb-2.5 text-[13px] font-bold text-neutral-tertiary">
                {centerLabel}
              </div>
              <div className="flex flex-col lg:min-h-0 lg:flex-1">{center}</div>
            </section>

            {right && (
              <section className="flex flex-col lg:min-h-0 lg:w-[332px] lg:shrink-0">
                <h2 className="shrink-0 pb-2.5 text-[13px] font-bold text-neutral-tertiary lg:px-2">
                  {rightLabel}
                </h2>
                {/*
                 * 안쪽 목록이 스크롤되려면 이 박스 높이가 확정돼야 한다.
                 * `max-h-full` 은 h2 를 포함한 열 전체 높이를 기준으로 잡혀서
                 * 실제로는 아무것도 제한하지 못했다. `flex-1` 로 남은 공간을 채운다.
                 */}
                {/*
                 * 안쪽 여백을 두지 않는다. 빠른 입찰·공유 패널이 이 테두리에
                 * 딱 맞게 들어가야 "테두리가 감싼" 것처럼 보인다. 여백이 필요한
                 * 리더보드 쪽에서만 자기 패딩을 준다.
                 */}
                <div className="flex flex-col overflow-hidden rounded-2xl border lg:min-h-0 lg:flex-1">
                  {right}
                </div>
              </section>
            )}

            {overlay && (
              // 열 경계보다 3px 만 넉넉하게. 딱 맞추면 가장자리가 어중간하게 걸친다.
              <div className="animate-panel absolute -inset-[3px] z-20 flex flex-col">
                {overlay}
              </div>
            )}
          </div>
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
        className="ml-auto flex h-8 items-center rounded-lg bg-brand-500 px-3.5 text-[13px] font-bold text-white transition-opacity hover:opacity-90"
      >
        로그인
      </Link>
    </div>
  )
}
