import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { ImagePlus, Minus, Store } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import {
  ItemPickerModal,
  type PickedItem,
} from '@/features/seller/components/item-picker-modal'
import { useAddAll } from '@/api/generated/경매-물품/경매-물품'
import { useCreate2 } from '@/api/generated/경매방/경매방'
import {
  toAuctionRoomErrorMessage,
  toFailureReason,
} from '@/features/seller/auction-room-error'
import { toast } from '@/lib/toast'
import { NumberField, TextAreaField, TextField } from '@/components/ui/field'
import { requireMember } from '@/lib/route-guards'
import { RoutePending } from '@/components/route-states'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'

/**
 * 경매방 생성 (Figma `WEB-08 · 판매자 · 경매방 생성`).
 *
 * 위쪽은 기본 정보 / 입찰 규칙 두 카드가 나란히, 아래는 전체 폭 판매 물품
 * 카드다. 입찰 단위는 방 단위로 한 번만 정한다.
 *
 * 방을 만든 뒤 물품을 벌크로 넣는 2단계다. 물품 일부가 거절돼도 방은 남으므로
 * 만들어진 `roomId` 를 들고 있다가 재시도한다 — 없으면 누를 때마다 빈 방이 쌓인다.
 */
export const Route = createFileRoute('/seller/rooms/new')({
  beforeLoad: requireMember,
  component: AuctionRoomNewPage,
})

/** 서버의 경매방당 물품 상한(`AuctionItemRepository.MAX_SUMMARY_SIZE`)과 같은 값. */
const MAX_ITEMS = 100

function AuctionRoomNewPage() {
  const navigate = useNavigate()
  const { profile, isPending: profilePending } = useMySellerProfile()

  const [title, setTitle] = useState('')
  const [intro, setIntro] = useState('')
  const [bidUnit, setBidUnit] = useState(1000)
  const [thresholdMinutes, setThresholdMinutes] = useState(1)
  const [extendMinutes, setExtendMinutes] = useState(1)
  const [items, setItems] = useState<PickedItem[]>([])
  const [picking, setPicking] = useState(false)
  const [titleError, setTitleError] = useState<string | null>(null)

  // 방을 만든 뒤 물품 추가가 실패하면 방은 그대로 남는다. 그 번호를 들고 있다가
  // 재시도할 때 재사용한다 — 없으면 다시 누를 때마다 빈 방이 하나씩 쌓인다.
  const [roomId, setRoomId] = useState<number | null>(null)

  const createRoom = useCreate2()
  const addItems = useAddAll()
  const creating = createRoom.isPending || addItems.isPending

  if (profilePending) return <RoutePending />

  // 판매자 프로필이 없으면 리다이렉트하지 않고 안내 화면을 보여준다.
  // 조회에 실패한 경우도 여기로 온다 — 프로필 없이 방을 만들 수는 없어서다.
  if (!profile) {
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
                className="ease-soft inline-block rounded-2xl bg-brand-500 px-6 py-3.5 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
              >
                판매자 프로필 등록하기
              </Link>
            }
          />
        </div>
      </AppShell>
    )
  }

  const canSubmit = title.trim().length >= 2 && items.length > 0

  // 방은 이미 만들어졌고 물품만 남은 상태면 버튼이 재시도라는 걸 드러낸다.
  const submitLabel =
    roomId === null ? '경매방 만들기' : `물품 ${items.length}개 다시 추가`

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (title.trim().length < 2) {
      setTitleError('경매방 이름을 2자 이상 입력해주세요.')
      return
    }
    if (items.length === 0) return

    try {
      // 방이 이미 있으면(= 직전 시도에서 물품만 실패) 다시 만들지 않는다.
      let targetRoomId = roomId
      if (targetRoomId === null) {
        const created = await createRoom.mutateAsync({
          data: {
            name: title.trim(),
            description: intro.trim() || undefined,
            bidIncrement: bidUnit,
            // 화면은 분, 서버는 초로 받는다.
            softCloseTriggerSeconds: thresholdMinutes * 60,
            softCloseExtendSeconds: extendMinutes * 60,
          },
        })

        targetRoomId = created.data?.auctionRoomId ?? null
        if (targetRoomId === null) throw new Error('auctionRoomId가 비어 있다')
        setRoomId(targetRoomId)
      }

      const result = await addItems.mutateAsync({
        auctionRoomId: targetRoomId,
        data: {
          items: items.map((item) => ({
            productId: item.productId,
            startingPrice: item.startingPrice,
          })),
        },
      })

      const failed = result.data?.failed ?? []
      if (failed.length === 0) {
        void navigate({
          to: '/seller/rooms/$roomId/created',
          params: { roomId: String(targetRoomId) },
        })
        return
      }

      // 들어간 물품은 목록에서 지우고 거절된 것만 남겨 그대로 다시 시도하게 한다.
      const failedIds = new Set(failed.map((failure) => failure.productId))
      setItems((prev) => prev.filter((item) => failedIds.has(item.productId)))
      toast.error(`물품 ${failed.length}개를 추가하지 못했어요`, {
        description: toFailureReason(failed[0]?.code),
      })
    } catch (error) {
      const message = toAuctionRoomErrorMessage(error)
      toast.error(message.title, { description: message.description })
    }
  }

  return (
    <AppShell title="경매방 만들기" back className="max-w-[1280px]">
      <PageHeader
        title="새 경매방 만들기"
        description="경매방 정보와 입찰 규칙을 설정하고 판매할 물품을 추가하세요."
      />

      <form
        onSubmit={(event) => void handleSubmit(event)}
        className="mt-6 flex flex-col gap-6"
      >
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
                {/*
                  이미지 업로드 수단이 아직 없어 서버로 보내지 않는다(별도 작업).
                  누를 수 있게 두면 눌러도 아무 일이 없어서 고장으로 보인다.
                */}
                <div className="mt-2.5 flex h-[184px] w-full flex-col items-center justify-center gap-2 rounded-2xl border border-dashed bg-surface-subtle text-neutral-muted">
                  <ImagePlus aria-hidden className="size-6" />
                  <span className="text-[14px] font-bold">준비 중이에요</span>
                  <span className="text-[12px] font-medium">
                    커버 이미지는 곧 추가할 수 있어요
                  </span>
                </div>
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
              {items.map((item) => (
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
                    {item.name}
                  </p>

                  {/* 시작가 수정은 모달에서 한다 — "물품 추가·수정"으로 다시 연다. */}
                  <p className="shrink-0 text-[14px] font-semibold tabular-nums text-neutral-secondary">
                    시작가 {item.startingPrice.toLocaleString('ko-KR')}원
                  </p>

                  <button
                    type="button"
                    aria-label={`${item.name} 제외`}
                    onClick={() =>
                      setItems((prev) =>
                        prev.filter(
                          (candidate) => candidate.productId !== item.productId,
                        ),
                      )
                    }
                    className="ease-soft flex size-8 shrink-0 items-center justify-center rounded-2xl bg-live-surface text-live transition-all duration-150 hover:opacity-80 active:scale-95"
                  >
                    <Minus aria-hidden className="size-4" strokeWidth={3} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            disabled={items.length >= MAX_ITEMS}
            onClick={() => setPicking(true)}
            className="ease-soft mt-6 h-13 w-full rounded-[14px] border border-brand-200 bg-card text-[14px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {items.length > 0 ? '물품 추가·수정' : '+ 물품 추가'}
          </button>

          <p className="mt-3 text-center text-[12px] font-medium text-neutral-muted">
            {items.length >= MAX_ITEMS
              ? `한 경매방에는 최대 ${MAX_ITEMS}개까지 담을 수 있어요.`
              : `등록한 상품에서 골라 추가하세요. 최대 ${MAX_ITEMS}개까지 추가할 수 있어요.`}
          </p>
        </section>

        <button
          type="submit"
          disabled={!canSubmit || creating}
          className="ease-soft h-14 w-full rounded-[14px] bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
        >
          {creating ? '만드는 중…' : submitLabel}
        </button>
      </form>

      {/* 물품 추가 — 등록한 상품 고르기 (WEB-09) */}
      {/*
        이미 고른 물품도 체크된 채로 보여준다. 그래야 시작가를 고치거나 뺄 수 있다.
        모달이 고른 것 전체를 돌려주므로 목록을 통째로 교체한다.
      */}
      <ItemPickerModal
        open={picking}
        onClose={() => setPicking(false)}
        initialItems={items}
        onConfirm={setItems}
      />
    </AppShell>
  )
}
