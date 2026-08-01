import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ImagePlus, Minus, Store } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { ItemPickerModal } from '@/features/seller/components/item-picker-modal'
import { NumberField, TextAreaField, TextField } from '@/components/ui/field'
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

            <div className="mt-6">
              <TextField
                label="경매방 이름"
                required
                hint="참여자에게 보이는 이름이에요."
                error={titleError ?? undefined}
                value={title}
                placeholder="예: 7월 한정 라이브 경매"
                onChange={(event) => {
                  setTitle(event.target.value)
                  setTitleError(null)
                }}
              />
            </div>

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
                <TextAreaField
                  label="소개"
                  value={intro}
                  className="h-[184px]"
                  placeholder="경매방을 간단히 소개해 주세요."
                  onChange={(event) => setIntro(event.target.value)}
                />
              </div>
            </div>
          </section>

          {/* 입찰 규칙 */}
          <section className="rounded-[20px] border bg-card p-7">
            <h2 className="text-[19px] font-extrabold text-foreground">
              입찰 규칙
            </h2>

            <div className="mt-6">
              <NumberField
                label="입찰 단위"
                unit="원"
                required
                hint="첫 입찰은 시작가 + 입찰 단위부터 가능해요."
                steps={[1000, 5000, 10000]}
                min={1000}
                value={bidUnit}
                onValueChange={setBidUnit}
              />
            </div>

            <h3 className="mt-8 text-[14px] font-bold text-foreground">
              마감 연장
            </h3>
            <p className="mt-2.5 text-[13px] font-medium text-neutral-tertiary">
              종료 직전 입찰이 발생하면 마감 시간을 자동으로 연장합니다.
            </p>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <NumberField
                label="마감 임박 기준"
                unit="분"
                steps={[1]}
                min={1}
                value={thresholdMinutes}
                onValueChange={setThresholdMinutes}
              />
              <NumberField
                label="연장 시간"
                unit="분"
                steps={[1]}
                min={1}
                value={extendMinutes}
                onValueChange={setExtendMinutes}
              />
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

                    <div className="w-full shrink-0 sm:w-[220px]">
                      <label
                        htmlFor={`start-price-${item.productId}`}
                        className="block text-[11px] font-semibold text-neutral-tertiary"
                      >
                        시작가
                      </label>
                      <div className="mt-1 flex items-center gap-2">
                        <input
                          id={`start-price-${item.productId}`}
                          inputMode="numeric"
                          value={item.startPrice.toLocaleString('ko-KR')}
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
                          className="h-11 min-w-0 flex-1 rounded-xl border bg-card px-3 text-right text-[14px] font-semibold outline-none focus-visible:border-brand-400"
                        />
                        <span className="shrink-0 text-[13px] font-semibold text-neutral-tertiary">
                          원
                        </span>
                      </div>
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

      {/* 물품 추가 — 등록한 상품 고르기 (WEB-09) */}
      <ItemPickerModal
        open={picking}
        onClose={() => setPicking(false)}
        products={availableProducts}
        onConfirm={(productIds) =>
          setItems((prev) => [
            ...prev,
            // 시작가는 Figma 안내대로 이 화면에서 정한다.
            ...productIds.map((productId) => ({
              productId,
              startPrice: 10000,
            })),
          ])
        }
      />
    </AppShell>
  )
}
