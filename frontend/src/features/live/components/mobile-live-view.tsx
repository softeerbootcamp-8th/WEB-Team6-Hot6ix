import { ChevronLeft, Share2, Square } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { ConnectionBanner } from '@/features/live/components/connection-banner'
import { MobileEventFeed } from '@/features/live/components/mobile-event-feed'
import { MobileNavDrawer } from '@/components/layout/mobile-nav-drawer'
import { GuestNotice } from '@/features/live/components/live-shell'
import { ItemLeaderboard } from '@/features/live/components/leaderboard'
import { LiveItemList } from '@/features/live/components/live-item-list'
import { cn } from '@/lib/utils'
import type { RealtimeStatus } from '@/features/live/use-realtime-status'
import type {
  AuctionItemDetail,
  AuctionRoomDetail,
  RoomEvent,
} from '@/mocks/types'

/**
 * 모바일 라이브 경매방 (Figma `MOB-04 · 구매자 · 라이브`, 713:4862).
 *
 * 웹(WEB-09)의 3열을 접은 게 아니라 별도 구성이다.
 * 전용 앱바(64) → 진행 중인 물품 목록 → 실시간 현황 카드(세그먼트로
 * 이벤트/리더보드 전환) → 하단 고정 입찰 바(88).
 *
 * 웹 화면은 `LiveShell` 이 그대로 담당한다. 라우트에서 `md` 기준으로 갈린다.
 */
export function MobileLiveView({
  room,
  status,
  isGuest,
  isOwner = false,
  events,
  items,
  itemsPlaceholder = null,
  liveItems,
  rankedItems,
  justClosedId = null,
  devTools,
  onRetry,
  onShare,
  onCloseRoom,
  onBack,
  onOpenItem,
  onBid,
}: {
  room: AuctionRoomDetail
  status: RealtimeStatus
  isGuest: boolean
  /** 방을 만든 사람은 자기 방에 입찰할 수 없다. */
  isOwner?: boolean
  events: RoomEvent[]
  /** 방에 편성된 전체 물품. 목록은 데스크톱과 같은 묶음으로 보여준다. */
  items: AuctionItemDetail[]
  /** 목록을 아직 못 받았을 때 대신 그릴 것(스켈레톤·재시도). 정상이면 넘기지 않는다. */
  itemsPlaceholder?: ReactNode
  /** 진행 중인 물품. 입찰 버튼 활성화에 쓴다. */
  liveItems: AuctionItemDetail[]
  /** 리더보드 탭에 쌓을 물품 (진행 중 + 종료) */
  rankedItems: AuctionItemDetail[]
  /** 방금 마감된 물품 id. 카드에 "경매 종료" 도장을 찍는다. */
  justClosedId?: number | null
  /** 개발용 조작. 프로덕션에서는 넘기지 않는다. */
  devTools?: {
    sellerView: boolean
    onSellerViewChange: (next: boolean) => void
    onDemoBid: (shuffle: boolean) => void
  }
  onRetry: () => void
  onShare: () => void
  /** 판매자만 받는다. 방 전체를 끝낸다. */
  onCloseRoom?: () => void
  onBack: () => void
  onOpenItem: (itemId: number) => void
  onBid: () => void
}) {
  const [tab, setTab] = useState<'events' | 'leaderboard'>('events')

  /*
   * 화면 높이에 맞춰 두고 아래에 한 뼘(64px)만 여유를 둔다.
   *
   * 완전히 잠그면 굳은 느낌이고, 20px 만 주면 손가락이 멈칫하다 끝나서
   * 오히려 뚝뚝 끊긴다. 한 번 쓸어내릴 만큼은 움직이되 내용은 그대로
   * 남는 정도가 가장 부드럽다. 문서 잠금·고무줄 차단은 하지 않는다.
   */

  return (
    <>
      {/*
       * 화면 높이에 맞춰 두고, 물품 목록과 현황 카드가 각자 자기 영역
       * 안에서만 스크롤된다.
       */}
      <div className="flex h-[100dvh] flex-col overflow-hidden bg-background">
        {/* 앱바 — 375×64 */}
        <header className="z-30 flex h-16 shrink-0 items-center gap-2 border-b bg-card px-4">
          <button
            type="button"
            onClick={onBack}
            aria-label="뒤로 가기"
            className="ease-soft -ml-1 flex size-8 shrink-0 items-center justify-center rounded-lg text-foreground transition-all duration-150 hover:bg-fill active:scale-90"
          >
            <ChevronLeft aria-hidden className="size-6" strokeWidth={2} />
          </button>

          <div className="min-w-0 flex-1">
            <p className="truncate text-[16px] font-bold text-foreground">
              {room.title}
            </p>
            <p className="mt-0.5 truncate text-[12px] font-medium text-neutral-tertiary">
              {room.participantCount}명 참여 중
            </p>
          </div>

          <button
            type="button"
            onClick={onShare}
            aria-label="경매방 공유"
            className="ease-soft flex size-9 shrink-0 items-center justify-center rounded-lg text-foreground transition-all duration-150 hover:bg-fill active:scale-90"
          >
            <Share2 aria-hidden className="size-5" />
          </button>

          {/* 방을 만든 사람만 보인다. */}
          {onCloseRoom && (
            <button
              type="button"
              onClick={onCloseRoom}
              aria-label="경매방 종료"
              className="ease-soft flex size-9 shrink-0 items-center justify-center rounded-lg text-live transition-all duration-150 hover:bg-live-surface active:scale-90"
            >
              <Square aria-hidden className="size-[18px]" />
            </button>
          )}

          {/* 라이브에서도 다른 섹션으로 나갈 수 있어야 한다 */}
          <MobileNavDrawer />
        </header>

        {/* 개발용 조작 — 데스크톱 헤더의 버튼들과 같은 역할이다 */}
        {import.meta.env.DEV && devTools && (
          <div className="flex shrink-0 items-center gap-1.5 overflow-x-auto border-b bg-fill px-4 py-2">
            <span className="shrink-0 text-[10px] font-extrabold text-neutral-muted">
              DEV
            </span>
            <span className="flex shrink-0 items-center rounded-[10px] border border-border-strong bg-card p-0.5">
              {(
                [
                  { key: false, label: '구매자' },
                  { key: true, label: '판매자' },
                ] as const
              ).map((option) => (
                <button
                  key={option.label}
                  type="button"
                  onClick={() => devTools.onSellerViewChange(option.key)}
                  aria-pressed={devTools.sellerView === option.key}
                  className={cn(
                    'ease-soft flex h-6 items-center rounded-lg px-2 text-[11px] font-bold transition-all duration-150 active:scale-95',
                    devTools.sellerView === option.key
                      ? 'bg-brand-50 text-brand-500'
                      : 'text-neutral-tertiary',
                  )}
                >
                  {option.label}
                </button>
              ))}
            </span>

            <button
              type="button"
              onClick={() => devTools.onDemoBid(false)}
              className="ease-soft flex h-7 shrink-0 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95"
            >
              이벤트
            </button>
            <button
              type="button"
              onClick={() => devTools.onDemoBid(true)}
              className="ease-soft flex h-7 shrink-0 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95"
            >
              순위 변동
            </button>
          </div>
        )}

        <main className="flex min-h-0 flex-1 flex-col px-4 pt-4">
          <ConnectionBanner status={status} onRetry={onRetry} />
          {isGuest && <GuestNotice redirectTo={`/rooms/${room.id}`} />}

          {/*
           * 데스크톱과 같은 목록을 쓴다. 진행 중만 보여주면 시작 전·종료 물품을
           * 볼 방법이 없고, 남은 시간도 카드가 직접 그려준다.
           */}
          <section className="flex min-h-0 flex-[4] flex-col">
            <div className="flex shrink-0 items-baseline justify-between gap-3">
              <h2 className="text-[13px] font-bold text-neutral-tertiary">
                물품 목록 ({items.length})
              </h2>
              <p className="text-[12px] font-semibold text-live">
                {liveItems.length}개 LIVE
              </p>
            </div>

            {itemsPlaceholder ??
              (items.length === 0 ? (
                <p className="mt-2 rounded-2xl border bg-card px-4 py-8 text-center text-[13px] font-medium text-neutral-muted">
                  아직 물품이 없어요.
                </p>
              ) : (
                <LiveItemList
                  items={items}
                  className="mt-2 min-h-0 flex-1"
                  // 판매자는 모바일에서도 시간 조절과 시작 버튼을 쓸 수 있어야 한다.
                  canStart={isOwner}
                  justClosedId={justClosedId}
                  onSelect={(item) => onOpenItem(item.id)}
                />
              ))}
          </section>

          {/* 목록과 현황 사이는 확실히 띄운다. 붙어 있으면 한 덩어리로 읽힌다. */}
          <h2 className="mt-5 shrink-0 text-[13px] font-bold text-neutral-tertiary">
            실시간 현황
          </h2>

          {/*
           * 세그먼트 + 내용이 한 장의 카드다 (Figma 는 3px 흰 띠로 이음새를 덮었다).
           * 높이를 뷰포트에 묶어 **카드 안에서만** 스크롤되게 한다.
           * 안 그러면 리더보드가 길어질 때 페이지 전체가 스크롤된다.
           */}
          <section className="mt-1.5 mb-3 flex min-h-0 flex-[6] flex-col overflow-hidden rounded-2xl border bg-card">
            <div
              role="tablist"
              aria-label="실시간 현황 보기"
              className="grid shrink-0 grid-cols-2 gap-2 border-b p-2"
            >
              {(
                [
                  { key: 'events', label: '경매방 이벤트' },
                  { key: 'leaderboard', label: '리더보드' },
                ] as const
              ).map((item) => (
                <button
                  key={item.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === item.key}
                  onClick={() => setTab(item.key)}
                  className={cn(
                    'ease-soft h-8 rounded-[9px] border text-[13px] transition-all duration-150 active:scale-95',
                    tab === item.key
                      ? 'border-brand-300 bg-brand-100 font-bold text-brand-500'
                      : 'border-transparent font-medium text-neutral-tertiary hover:bg-fill',
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>

            {/* 두 패널을 겹쳐 두고 교차시킨다. 숨은 쪽은 inert 로 빠진다. */}
            <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)]">
              <TabPanel active={tab === 'events'}>
                <MobileEventFeed events={events} />
              </TabPanel>

              <TabPanel active={tab === 'leaderboard'}>
                <div className="flex h-full flex-col p-4">
                  {rankedItems.length === 0 ? (
                    <p className="rounded-xl bg-surface-subtle px-4 py-8 text-center text-[13px] font-medium text-neutral-muted">
                      아직 시작한 물품이 없어요.
                    </p>
                  ) : (
                    <ul className="min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
                      {rankedItems.map((item) => (
                        <ItemLeaderboard
                          key={item.id}
                          item={item}
                          justClosed={justClosedId === item.id}
                        />
                      ))}
                    </ul>
                  )}
                </div>
              </TabPanel>
            </div>
          </section>
        </main>

        {/* 하단 고정 입찰 바 — 375×88 */}
        <div className="z-20 h-[88px] shrink-0 border-t bg-card px-4 pt-4">
          <button
            type="button"
            onClick={onBid}
            disabled={isGuest || isOwner || liveItems.length === 0}
            className="ease-soft h-13 w-full rounded-[14px] bg-brand-500 text-[16px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {isOwner ? '판매자는 입찰할 수 없어요' : '입찰하기'}
          </button>
        </div>
      </div>

      {/* 부드럽게 한 번 쓸어내릴 만큼의 여백 */}
      <div aria-hidden className="h-16 bg-background" />
    </>
  )
}

/** 세그먼트로 교차되는 패널 한 칸. `RightSlot`(웹)과 같은 방식이다. */
function TabPanel({
  active,
  children,
}: {
  active: boolean
  children: ReactNode
}) {
  return (
    <div
      inert={!active}
      className={cn(
        'ease-soft flex min-h-0 min-w-0 flex-col overflow-hidden [grid-area:1/1] transition-all duration-300',
        active
          ? 'translate-y-0 opacity-100'
          : 'pointer-events-none translate-y-2 opacity-0',
      )}
    >
      {children}
    </div>
  )
}
