import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ImagePlus, Minus, Store } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { Modal } from '@/components/ui/modal'
import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { useCurrentUser } from '@/lib/session'

/**
 * 경매방 생성 (Figma `WEB-08 · 판매자 · 경매방 생성`).
 *
 * 위쪽은 기본 정보 / 입찰 규칙 두 카드가 나란히, 아래는 전체 폭 판매 물품
 * 카드다. 물품 개수 제한은 없고, 입찰 단위는 방 단위로 한 번만 정한다.
 */
export const Route = createFileRoute('/seller/rooms/new')({
  beforeLoad: requireMember,
  component: AuctionRoomNewPage,
})

interface DraftItem {
  productId: number
  startPrice: number
}

function AuctionRoomNewPage() {
  const navigate = useNavigate()
  const user = useCurrentUser()

  const [title, setTitle] = useState('')
  const [intro, setIntro] = useState('')
  const [bidUnit, setBidUnit] = useState(1000)
  const [thresholdMinutes, setThresholdMinutes] = useState(1)
  const [extendMinutes, setExtendMinutes] = useState(1)
  const [items, setItems] = useState<DraftItem[]>([])
  const [picking, setPicking] = useState(false)
  const [titleError, setTitleError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  // 판매자 프로필이 없으면 리다이렉트하지 않고 안내 화면을 보여준다.
  if (!user?.sellerProfile) {
    return (
      <AppShell title="경매방 만들기" back>
        <PageHeader title="새 경매방 만들기" />
        <div className="mt-8">
          <EmptyState
            icon={<Store className="size-8" />}
            title="판매자 프로필이 필요해요"
            description="경매방을 열려면 가게명과 연락처가 먼저 등록되어 있어야 합니다."
            hint="등록은 1분이면 끝나요."
            action={
              <Link
                to="/seller/profile/new"
                className="ease-soft inline-block rounded-2xl bg-primary px-6 py-3.5 text-[15px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
              >
                판매자 프로필 등록하기
              </Link>
            }
          />
        </div>
      </AppShell>
    )
  }

  const availableProducts = MOCK_PRODUCTS.filter(
    (product) => !items.some((item) => item.productId === product.id),
  )

  const canSubmit = title.trim().length >= 2 && items.length > 0

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (title.trim().length < 2) {
      setTitleError('경매방 이름을 2자 이상 입력해주세요.')
      return
    }
    if (items.length === 0) return

    setCreating(true)
    // TODO: POST /api/v1/auction-rooms 연동 (현재 목업)
    window.setTimeout(() => {
      void navigate({
        to: '/seller/rooms/$roomId/created',
        params: { roomId: '1' },
      })
    }, 700)
  }

  const fieldClass =
    'h-13 w-full rounded-[14px] border bg-card px-4 text-[14px] font-medium outline-none placeholder:font-medium placeholder:text-neutral-muted focus-visible:border-brand-400'

  return (
    <AppShell title="경매방 만들기" back className="max-w-[1280px]">
      <PageHeader
        title="새 경매방 만들기"
        description="경매방 정보와 입찰 규칙을 설정하고 판매할 물품을 추가하세요."
      />

      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-6">
        <div className="grid gap-6 xl:grid-cols-2">
          {/* 기본 정보 */}
          <section className="rounded-[20px] border bg-card p-7">
            <h2 className="text-[19px] font-extrabold text-foreground">
              기본 정보
            </h2>

            <label
              htmlFor="room-title"
              className="mt-6 block text-[14px] font-bold text-foreground"
            >
              경매방 이름
            </label>
            <input
              id="room-title"
              value={title}
              placeholder="예: 7월 한정 라이브 경매"
              onChange={(event) => {
                setTitle(event.target.value)
                setTitleError(null)
              }}
              aria-invalid={titleError !== null}
              aria-describedby="room-title-help"
              className={cn('mt-2.5', fieldClass)}
            />
            <p
              id="room-title-help"
              className={cn(
                'mt-2 text-[12px] font-medium',
                titleError ? 'text-live' : 'text-neutral-muted',
              )}
            >
              {titleError ?? '참여자에게 보이는 이름이에요.'}
            </p>

            <div className="mt-6 grid gap-6 sm:grid-cols-[240px_minmax(0,1fr)]">
              <div>
                <span className="block text-[14px] font-bold text-foreground">
                  대표 이미지
                </span>
                <button
                  type="button"
                  className="ease-soft mt-2.5 flex h-[184px] w-full flex-col items-center justify-center gap-2 rounded-2xl border border-brand-200 bg-brand-50 text-brand-500 transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
                >
                  <ImagePlus aria-hidden className="size-6" />
                  <span className="text-[14px] font-bold">
                    커버 이미지 추가
                  </span>
                </button>
              </div>

              <div>
                <label
                  htmlFor="room-intro"
                  className="block text-[14px] font-bold text-foreground"
                >
                  소개
                </label>
                <textarea
                  id="room-intro"
                  value={intro}
                  placeholder="경매방을 간단히 소개해 주세요."
                  onChange={(event) => setIntro(event.target.value)}
                  className="mt-2.5 h-[184px] w-full resize-none rounded-[14px] border bg-card px-4 py-3.5 text-[14px] font-medium outline-none placeholder:text-neutral-muted focus-visible:border-brand-400"
                />
              </div>
            </div>
          </section>

          {/* 입찰 규칙 */}
          <section className="rounded-[20px] border bg-card p-7">
            <h2 className="text-[19px] font-extrabold text-foreground">
              입찰 규칙
            </h2>

            <label
              htmlFor="bid-unit"
              className="mt-6 block text-[14px] font-bold text-foreground"
            >
              입찰 단위
            </label>
            <input
              id="bid-unit"
              inputMode="numeric"
              value={`${bidUnit.toLocaleString('ko-KR')}원`}
              onChange={(event) =>
                setBidUnit(Number(event.target.value.replace(/\D/g, '')) || 0)
              }
              className={cn('mt-2.5 font-semibold', fieldClass)}
            />

            <h3 className="mt-8 text-[14px] font-bold text-foreground">
              마감 연장
            </h3>
            <p className="mt-2.5 text-[13px] font-medium text-neutral-tertiary">
              종료 직전 입찰이 발생하면 마감 시간을 자동으로 연장합니다.
            </p>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <div>
                <label
                  htmlFor="threshold"
                  className="block text-[13px] font-semibold text-foreground"
                >
                  마감 임박 기준
                </label>
                <input
                  id="threshold"
                  inputMode="numeric"
                  value={`${thresholdMinutes}분`}
                  onChange={(event) =>
                    setThresholdMinutes(
                      Number(event.target.value.replace(/\D/g, '')) || 0,
                    )
                  }
                  className={cn('mt-2.5 font-semibold', fieldClass)}
                />
              </div>
              <div>
                <label
                  htmlFor="extend"
                  className="block text-[13px] font-semibold text-foreground"
                >
                  연장 시간
                </label>
                <input
                  id="extend"
                  inputMode="numeric"
                  value={`${extendMinutes}분`}
                  onChange={(event) =>
                    setExtendMinutes(
                      Number(event.target.value.replace(/\D/g, '')) || 0,
                    )
                  }
                  className={cn('mt-2.5 font-semibold', fieldClass)}
                />
              </div>
            </div>

            <p className="mt-6 rounded-[14px] bg-brand-50 px-4 py-4 text-[13px] font-semibold text-brand-500">
              물품별 누적 연장은 최대 1시간까지 적용됩니다.
            </p>
          </section>
        </div>

        {/* 판매 물품 */}
        <section className="rounded-[20px] border bg-card p-7">
          <div className="flex items-baseline">
            <h2 className="text-[19px] font-extrabold text-foreground">
              판매 물품
            </h2>
            <span className="ml-auto text-[13px] font-bold text-brand-500">
              {items.length}개 선택됨
            </span>
          </div>

          {items.length > 0 && (
            <ul className="mt-6 space-y-7">
              {items.map((item) => {
                const product = MOCK_PRODUCTS.find(
                  (candidate) => candidate.id === item.productId,
                )
                if (!product) return null

                return (
                  <li
                    key={item.productId}
                    className="flex flex-wrap items-center gap-4"
                  >
                    <span
                      aria-hidden
                      className="flex size-16 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                    >
                      <ImagePlus className="size-5" />
                    </span>

                    <p className="min-w-0 flex-1 truncate text-[15px] font-bold text-foreground">
                      {product.name}
                    </p>

                    <div className="w-[220px] shrink-0">
                      <label
                        htmlFor={`start-price-${item.productId}`}
                        className="block text-[11px] font-semibold text-neutral-tertiary"
                      >
                        시작가
                      </label>
                      <input
                        id={`start-price-${item.productId}`}
                        inputMode="numeric"
                        value={`${item.startPrice.toLocaleString('ko-KR')}원`}
                        onChange={(event) =>
                          setItems((prev) =>
                            prev.map((candidate) =>
                              candidate.productId === item.productId
                                ? {
                                    ...candidate,
                                    startPrice:
                                      Number(
                                        event.target.value.replace(/\D/g, ''),
                                      ) || 0,
                                  }
                                : candidate,
                            ),
                          )
                        }
                        className="mt-1.5 h-10 w-full rounded-xl border border-brand-200 bg-card px-4 text-[14px] font-bold outline-none focus-visible:border-brand-400"
                      />
                    </div>

                    <button
                      type="button"
                      aria-label={`${product.name} 제외`}
                      onClick={() =>
                        setItems((prev) =>
                          prev.filter(
                            (candidate) =>
                              candidate.productId !== item.productId,
                          ),
                        )
                      }
                      className="ease-soft flex size-8 shrink-0 items-center justify-center rounded-2xl bg-live-surface text-live transition-all duration-150 hover:opacity-80 active:scale-95"
                    >
                      <Minus aria-hidden className="size-4" strokeWidth={3} />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}

          <button
            type="button"
            onClick={() => setPicking(true)}
            className="ease-soft mt-6 h-13 w-full rounded-[14px] border border-brand-200 bg-card text-[14px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-[0.99]"
          >
            + 물품 추가
          </button>

          {items.length === 0 && (
            <p className="mt-3 text-center text-[12px] font-medium text-neutral-muted">
              등록한 상품에서 골라 추가하세요. 개수 제한은 없습니다.
            </p>
          )}
        </section>

        <button
          type="submit"
          disabled={!canSubmit || creating}
          className="ease-soft h-14 w-full rounded-[14px] bg-primary text-[15px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
        >
          {creating ? '만드는 중…' : '경매방 만들기'}
        </button>
      </form>

      {/* 물품 추가 — 등록한 상품 고르기 */}
      <Modal
        open={picking}
        onClose={() => setPicking(false)}
        labelledBy="pick-item-title"
        className="max-w-[480px]"
      >
        <h2
          id="pick-item-title"
          className="text-[17px] font-bold text-foreground"
        >
          물품 추가
        </h2>
        <p className="mt-1.5 text-[13px] font-medium text-neutral-tertiary">
          등록한 상품에서 골라주세요.
        </p>

        {availableProducts.length === 0 ? (
          <p className="mt-5 rounded-2xl bg-surface-subtle px-4 py-8 text-center text-[13px] font-medium text-neutral-muted">
            추가할 수 있는 상품이 없어요.{' '}
            <Link
              to="/seller/products/new"
              className="font-bold text-brand-500 hover:underline"
            >
              상품 등록하기
            </Link>
          </p>
        ) : (
          <ul className="mt-5 max-h-[320px] space-y-2 overflow-y-auto">
            {availableProducts.map((product) => (
              <li key={product.id}>
                <button
                  type="button"
                  onClick={() => {
                    setItems((prev) => [
                      ...prev,
                      { productId: product.id, startPrice: 10000 },
                    ])
                    setPicking(false)
                  }}
                  className="ease-soft flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-all duration-150 hover:border-border-strong active:scale-[0.99]"
                >
                  <span
                    aria-hidden
                    className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-500"
                  >
                    <ImagePlus className="size-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[14px] font-bold text-foreground">
                      {product.name}
                    </span>
                    <span className="block text-[12px] font-medium text-neutral-muted">
                      {product.category}
                    </span>
                  </span>
                  <span className="shrink-0 text-[13px] font-bold text-brand-500">
                    추가
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}

        <p className="mt-5 text-center text-[12px] font-medium text-neutral-muted">
          첫 입찰은 시작가 + {formatWon(bidUnit)}부터 가능해요.
        </p>
      </Modal>
    </AppShell>
  )
}
