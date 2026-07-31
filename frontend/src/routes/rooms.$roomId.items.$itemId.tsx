import { Clock, ImageIcon, Loader2, Search, Share2, X } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'

import { ConnectionBanner } from '@/features/live/components/connection-banner'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemEventList } from '@/features/live/components/item-event-list'
import { LeaderboardRows } from '@/features/live/components/leaderboard-rows'
import { LiveItemCard } from '@/features/live/components/live-item-card'
import { MOCK_ROOM_DETAIL, MOCK_ROOM_EVENTS } from '@/mocks/data'
import { formatRemaining, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import { useCurrentUser } from '@/lib/session'
import { useRealtimeStatus } from '@/features/live/use-realtime-status'

/**
 * 물품 상세 (Figma `WEB-13 · 구매자 · 물품 상세 (LIVE)`).
 *
 * 왼쪽 물품 목록은 그대로 두고, 가운데부터 오른쪽 끝까지 하나의 상세 패널
 * (896×590)이 차지한다. 패널 안은 다시 두 열로 나뉜다.
 * - 왼쪽 360: 상품 이미지·제목·현재 최고가·링크·설명
 * - 오른쪽 476: 실시간 리더보드 카드 → 물품 이벤트 카드 → 퀵입찰 행
 */
export const Route = createFileRoute('/rooms/$roomId/items/$itemId')({
  component: AuctionItemPage,
})

/** Figma 퀵입찰 칩: 입찰 단위의 1배 / 5배. 누를 때마다 그만큼 더해진다. */
const PRESETS = [1, 5] as const

function AuctionItemPage() {
  const { roomId, itemId } = Route.useParams()
  const navigate = useNavigate()
  const user = useCurrentUser()
  const { status, retry } = useRealtimeStatus()

  const room = MOCK_ROOM_DETAIL
  const isGuest = user === null

  const [keyword, setKeyword] = useState('')
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<{
    tone: 'success' | 'error'
    message: string
  } | null>(null)

  const item =
    room.items.find((candidate) => String(candidate.id) === itemId) ??
    room.items[0]!

  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const ready = item.status === 'READY'
  const urgent = !closed && isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return room.items
    return room.items.filter((candidate) => candidate.name.includes(trimmed))
  }, [room.items, keyword])

  const itemEvents = useMemo(
    () =>
      MOCK_ROOM_EVENTS.filter(
        (event) =>
          event.subtitle === item.name || event.message.includes(item.name),
      ),
    [item.name],
  )

  const bidBlocked = closed || ready || amount < minimum

  const submitBid = () => {
    setPending(true)
    setFeedback(null)

    // TODO: POST /api/v1/auction-items/{id}/bids 연동 (현재 목업)
    window.setTimeout(() => {
      setPending(false)
      if (amount <= item.currentPrice) {
        setFeedback({
          tone: 'error',
          message:
            '이미 더 높은 입찰이 있어요. 최신 현재가로 다시 시도해주세요.',
        })
      } else {
        setFeedback({
          tone: 'success',
          message: `${formatWon(amount)} 입찰이 등록됐어요.`,
        })
      }
    }, 1200)
  }

  return (
    <LiveShell
      room={room}
      status={status}
      isGuest={isGuest}
      leftLabel={`물품 목록 (${room.items.length})`}
      left={
        <div className="flex h-full flex-col">
          <div className="relative shrink-0">
            <Search
              aria-hidden
              className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-neutral-muted"
            />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="물품 검색"
              aria-label="물품 검색"
              className="h-10 w-full rounded-xl border bg-card pr-3 pl-9 text-[13px] font-normal outline-none placeholder:text-neutral-muted focus-visible:border-ring"
            />
          </div>

          <ul className="mt-2.5 max-h-[420px] space-y-2 overflow-y-auto pr-0.5 lg:max-h-none lg:min-h-0 lg:flex-1">
            {visibleItems.map((candidate) => (
              <LiveItemCard
                key={candidate.id}
                item={candidate}
                selected={candidate.id === item.id}
                onSelect={() =>
                  void navigate({
                    to: '/rooms/$roomId/items/$itemId',
                    params: { roomId, itemId: String(candidate.id) },
                  })
                }
              />
            ))}
          </ul>
        </div>
      }
      centerLabel={
        <>
          <span>선택한 물품 상세 · 라이브</span>
          <Link
            to="/rooms/$roomId"
            params={{ roomId }}
            aria-label="상세 닫고 라이브로 돌아가기"
            className="ease-soft ml-auto flex size-7 items-center justify-center rounded-full border border-[#c2c9d6] bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
          >
            <X aria-hidden className="size-3.5" />
          </Link>
        </>
      }
      center={
        <>
          <ConnectionBanner status={status} onRetry={retry} />
          {isGuest && (
            <GuestNotice redirectTo={`/rooms/${roomId}/items/${itemId}`} />
          )}

          <div className="animate-rise rounded-[20px] border bg-card p-5 lg:min-h-0 lg:flex-1 lg:overflow-hidden">
            <div className="grid gap-5 lg:h-full xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
              {/* 왼쪽 · 상품 정보 */}
              <div className="min-w-0 overflow-y-auto">
                <div className="relative">
                  <span
                    aria-hidden
                    className="flex h-[220px] items-center justify-center rounded-xl bg-[#e8e8e8] text-neutral-muted"
                  >
                    <ImageIcon className="size-8" />
                  </span>
                  <span
                    className={cn(
                      'absolute top-3 left-3 flex h-[22px] items-center justify-center rounded px-2.5 text-[11px] font-bold text-white',
                      closed
                        ? 'bg-neutral-muted'
                        : ready
                          ? 'bg-notice'
                          : 'bg-live',
                    )}
                  >
                    {closed ? '경매 종료' : ready ? '시작 전' : 'LIVE'}
                  </span>
                </div>

                <div className="mt-5 flex items-baseline gap-2">
                  <h2 className="min-w-0 truncate text-[17px] font-bold text-foreground">
                    {item.name}
                  </h2>
                  <span className="ml-auto shrink-0 text-[12px] font-medium text-neutral-tertiary">
                    {closed ? '낙찰가' : '현재 최고가'}
                  </span>
                  <span className="shrink-0 text-[14px] font-bold tabular-nums text-brand-500">
                    {formatWon(item.currentPrice)}
                  </span>
                </div>

                {item.productUrl && (
                  <a
                    href={`https://${item.productUrl}`}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="mt-4 flex items-center gap-1.5 text-[13px] font-semibold text-brand-500 hover:underline"
                  >
                    <Share2 aria-hidden className="size-3.5" />
                    상품 링크 · {item.productUrl}
                  </a>
                )}

                <h3 className="mt-5 text-[13px] font-bold text-foreground">
                  상품 설명
                </h3>
                <p className="mt-1.5 text-[13px] leading-[1.6] font-normal text-neutral-secondary">
                  {item.description}
                </p>
              </div>

              {/* 오른쪽 · 리더보드 → 이벤트 → 퀵입찰 */}
              <div className="flex min-w-0 flex-col gap-4 lg:min-h-0">
                <section className="rounded-2xl border p-5">
                  <div className="flex items-baseline">
                    <h3 className="text-[13px] font-bold text-neutral-tertiary">
                      실시간 리더보드
                    </h3>
                    <span
                      className={cn(
                        'ml-auto flex items-center gap-1 text-[12px] font-semibold tabular-nums',
                        urgent ? 'text-live' : 'text-neutral-tertiary',
                      )}
                    >
                      <Clock aria-hidden className="size-[13px]" />
                      {closed
                        ? '마감됨'
                        : `남은 시간 ${formatRemaining(remaining)}`}
                    </span>
                  </div>

                  <div className="mt-3 border-t pt-3">
                    <LeaderboardRows entries={item.leaderboard} />
                  </div>
                </section>

                <ItemEventList events={itemEvents} itemName={item.name} />

                {/* 퀵입찰 행 — 상세 패널 오른쪽 열 안에 들어간다 */}
                {isGuest ? (
                  <Link
                    to="/"
                    search={{ redirect: `/rooms/${roomId}/items/${itemId}` }}
                    className="ease-soft flex h-[38px] items-center justify-center rounded-[10px] bg-primary text-[13px] font-semibold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
                  >
                    로그인하고 입찰하기
                  </Link>
                ) : (
                  <div className="flex gap-2">
                    {PRESETS.map((multiplier) => (
                      <button
                        key={multiplier}
                        type="button"
                        disabled={closed || ready}
                        // 누를 때마다 누적된다. 두 번 누르면 두 배만큼 오른다.
                        onClick={() =>
                          setAmount((prev) => prev + item.bidUnit * multiplier)
                        }
                        className="ease-soft h-[38px] w-[92px] shrink-0 rounded-[10px] border bg-card text-[12px] font-semibold tabular-nums text-foreground transition-all duration-150 hover:border-border-strong active:scale-95 disabled:opacity-50"
                      >
                        +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
                      </button>
                    ))}

                    <label className="w-[116px] shrink-0">
                      <span className="sr-only">입찰 금액 직접 입력</span>
                      <input
                        inputMode="numeric"
                        placeholder="직접 입력"
                        value={amount.toLocaleString('ko-KR')}
                        disabled={closed || ready}
                        onChange={(event) =>
                          setAmount(
                            Number(event.target.value.replace(/\D/g, '')) || 0,
                          )
                        }
                        className="h-[38px] w-full rounded-[10px] border bg-card px-2 text-right text-[12px] font-semibold tabular-nums outline-none placeholder:font-normal placeholder:text-neutral-muted focus-visible:border-brand-400 disabled:opacity-50"
                      />
                    </label>

                    <button
                      type="button"
                      disabled={bidBlocked || pending}
                      onClick={submitBid}
                      className="ease-soft flex h-[38px] min-w-0 flex-1 items-center justify-center gap-2 rounded-[10px] bg-primary text-[13px] font-semibold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
                    >
                      {pending && (
                        <Loader2 aria-hidden className="size-4 animate-spin" />
                      )}
                      {pending ? '처리 중…' : `입찰 ${formatWon(amount)}`}
                    </button>
                  </div>
                )}

                {!isGuest && amount < minimum && !closed && !ready && (
                  <p className="text-[12px] font-medium text-live">
                    최소 {formatWon(minimum)}부터 입찰할 수 있어요.
                  </p>
                )}

                {(closed || ready) && !isGuest && (
                  <p className="text-center text-[12px] font-medium text-neutral-tertiary">
                    {closed
                      ? '마감된 물품이에요.'
                      : '아직 시작하지 않은 물품이에요.'}
                  </p>
                )}

                {feedback && (
                  <p
                    role="status"
                    className={cn(
                      'animate-rise rounded-[10px] px-4 py-2.5 text-[12px] font-medium',
                      feedback.tone === 'success'
                        ? 'bg-success-surface text-success'
                        : 'bg-live-surface text-live',
                    )}
                  >
                    {feedback.message}
                  </p>
                )}
              </div>
            </div>
          </div>
        </>
      }
    />
  )
}
