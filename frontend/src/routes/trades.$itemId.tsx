import { createFileRoute } from '@tanstack/react-router'
import { keepPreviousData, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { EmptyState } from '@/components/page-header'
import { RouteError, RoutePending } from '@/components/route-states'
import {
  getGetCandidatesQueryKey,
  useComplete,
  useFail,
  useGetCandidates,
} from '@/api/generated/낙찰-후보/낙찰-후보'
import {
  getGetDealsQueryKey,
  useGetDeals,
} from '@/api/generated/거래-내역/거래-내역'
import { useGetDetail1 } from '@/api/generated/경매-물품/경매-물품'
import { useGetProfile } from '@/api/generated/판매자-프로필/판매자-프로필'
import {
  CANDIDATE_PAGE_SIZE,
  toCandidatePage,
  type CandidateRow,
  type CandidateStatus,
} from '@/features/trades/adapt-candidate'
import { toDeals } from '@/features/trades/adapt-deal'
import { toDealErrorMessage } from '@/features/trades/deal-error'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Pager } from '@/components/pager'
import { cn } from '@/lib/utils'
import { formatWon, josa } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { toast } from '@/lib/toast'

/**
 * 거래 상세.
 *
 * Figma 세 프레임을 한 라우트에서 다룬다. 보는 사람의 역할과 물품 결과로 갈린다.
 * - `WEB-02 · 구매자 · 거래 상세 / 최종 순위`      (843:12305)
 * - `WEB-03 · 판매자 · 거래 상세 / 낙찰 후보 관리` (843:12306)
 * - `WEB-04 · 판매자 · 거래 상세 / 낙찰 후보 없음` (713:4527)
 *
 * 셋 다 1280 폭에 400 물품 패널 + 24 간격 + 792 순위 패널, 패널 높이 560이다.
 * 오른쪽 패널은 5명씩 끊어 보여주고 아래에 페이지네이션이 붙는다.
 */
export const Route = createFileRoute('/trades/$itemId')({
  beforeLoad: requireMember,
  component: TradeDetailPage,
})

/**
 * 접근할 수 없는 물품은 장애가 아니라 안내다. 서버가 상태로 구분해 주므로
 * `RouteError` 로 날리지 않고 이유를 그대로 알려준다.
 */
function toAccessNotice(
  status: number | undefined,
): { title: string; description: string } | null {
  switch (status) {
    case 403:
      return {
        title: '내 거래가 아니에요',
        description: '이 물품의 판매자나 낙찰 후보만 볼 수 있어요.',
      }
    case 409:
      return {
        title: '아직 마감되지 않은 물품이에요',
        description: '경매가 끝나면 낙찰 후보를 확인할 수 있어요.',
      }
    case 404:
      return {
        title: '거래를 찾을 수 없어요',
        description: '삭제되었거나 접근할 수 없는 거래입니다.',
      }
    default:
      return null
  }
}

/**
 * 상태색.
 *
 * 거래 관련 색은 디자인 토큰(`--success` 등)과 값이 미묘하게 달라서 거래 내역
 * 화면(`trades.index.tsx`)과 마찬가지로 Figma 값을 그대로 쓴다.
 */
const TONE = {
  brand: { surface: 'bg-brand-50', text: 'text-brand-500' },
  // 내 행은 배경이 이미 brand-50 이라 같은 색 배지를 쓰면 안 보인다.
  mine: { surface: 'bg-brand-200', text: 'text-brand-600' },
  notice: {
    surface: 'bg-result-progress-surface',
    text: 'text-result-progress',
  },
  live: { surface: 'bg-result-failed-surface', text: 'text-live' },
  success: { surface: 'bg-result-won-surface', text: 'text-result-won' },
  muted: { surface: 'bg-fill', text: 'text-neutral-tertiary' },
} as const

type Tone = keyof typeof TONE

const CANDIDATE_META: Record<CandidateStatus, { label: string; tone: Tone }> = {
  IN_PROGRESS: { label: '거래 진행 중', tone: 'notice' },
  COMPLETED: { label: '거래 성사', tone: 'success' },
  FAILED: { label: '거래 실패', tone: 'live' },
  WAITING: { label: '대기', tone: 'muted' },
}

/**
 * 후보 한 명의 결과 칸.
 *
 * 대기 중인 후보를 "차순위"로 따로 짚지 않는다. 서버가 5명씩 끊어 주므로 진행 중인
 * 후보가 다른 페이지에 있으면 그 페이지의 첫 대기자가 차순위처럼 보인다 — 6순위를
 * 보고 있는데 실제 차순위는 4순위인 식이다. 승계 순서는 순위 자체가 이미 말해 주고,
 * "지금 차례"는 서버가 `IN_PROGRESS` 로 알려준다.
 *
 * 거래가 성사된 뒤에는 남은 대기자에게 차례가 오지 않는데 서버는 그들을 계속
 * `WAITING` 으로 준다. 그대로 "대기"라고 쓰면 아직 순서를 기다리는 것처럼 읽힌다.
 */
function toResultBadge(
  candidate: CandidateRow,
  settled: boolean,
): { label: string; tone: Tone } {
  if (candidate.dealStatus !== 'WAITING') {
    return CANDIDATE_META[candidate.dealStatus]
  }

  return settled
    ? { label: '기회 없음', tone: 'muted' }
    : CANDIDATE_META.WAITING
}

function TradeDetailPage() {
  const { itemId } = Route.useParams()
  const auctionItemId = Number(itemId)
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  // myRank 로 옮기는 건 한 번뿐이다. 그 뒤 사용자가 넘긴 페이지를 되돌리면 안 된다.
  const [jumpedToMyPage, setJumpedToMyPage] = useState(false)
  const [pendingAction, setPendingAction] = useState<
    'complete' | 'fail' | null
  >(null)

  const candidatesQuery = useGetCandidates(
    auctionItemId,
    { page },
    // 페이지를 넘길 때 표가 빈 상태로 깜빡이지 않게 이전 페이지를 잡아 둔다.
    { query: { placeholderData: keepPreviousData } },
  )
  const itemQuery = useGetDetail1(auctionItemId)
  /*
   * 물품 하나의 거래 요약을 주는 endpoint 가 없어 목록을 다시 쓴다. 쿼리 키가
   * 거래 내역 화면과 같아서, 목록에서 들어오면 캐시를 그대로 쓰고 요청이 없다.
   */
  const dealsQuery = useGetDeals()

  const list = useMemo(
    () => toCandidatePage(candidatesQuery.data?.data),
    [candidatesQuery.data],
  )
  const item = itemQuery.data?.data
  const deal = useMemo(
    () =>
      toDeals(dealsQuery.data?.data).find(
        (row) => row.auctionItemId === auctionItemId,
      ) ?? null,
    [dealsQuery.data, auctionItemId],
  )

  /*
   * 구매 건에서만 판매자 연락처를 조회한다. 판매 건의 sellerProfileId 는 내
   * 프로필이라 부를 이유가 없다. 거래 목록에는 연락처가 실려 오지 않는다 —
   * 거래와 무관한 화면까지 개인 정보를 들고 다니지 않기 위한 설계다.
   */
  const sellerProfileId =
    deal?.role === 'BUYER' ? (deal.sellerProfileId ?? null) : null
  const sellerProfileQuery = useGetProfile(sellerProfileId ?? 0, {
    query: { enabled: sellerProfileId != null },
  })
  const sellerPhone = sellerProfileQuery.data?.data?.storePhoneNumber ?? null

  /*
   * 내 순위는 목록 바깥으로 온다. 7위가 1페이지를 받아도 값이 있어서, 첫 응답을
   * 보고 내 페이지로 한 번 옮길 수 있다.
   */
  useEffect(() => {
    if (jumpedToMyPage || list.myRank == null) return

    setJumpedToMyPage(true)
    const target = Math.floor((list.myRank - 1) / CANDIDATE_PAGE_SIZE)
    if (target !== page) setPage(target)
  }, [jumpedToMyPage, list.myRank, page])

  const current =
    list.rows.find((row) => row.dealStatus === 'IN_PROGRESS') ?? null

  /*
   * 거래 단계는 후보 목록에서 판단한다. 표에 "거래 성사"가 보이는데 배지가 다른
   * 말을 하면 안 된다. 거래 내역은 성사된 후보가 다른 페이지에 있을 때를 위한
   * 보완일 뿐이고, 그 응답이 없다고 해서 실패로 단정하지 않는다.
   */
  const settled =
    list.rows.some((row) => row.dealStatus === 'COMPLETED') ||
    deal?.status === 'COMPLETED'

  /*
   * 성사·실패는 서버가 확정한 뒤에만 화면에 반영한다. 실패는 차순위 승계까지
   * 서버가 계산하므로 화면이 흉내 낼 수도 없다 (루트 CLAUDE.md 규칙).
   *
   * 거래 내역도 같이 무효화한다. 한 물품을 처리하면 목록의 거래 상태와 거래
   * 상대가 함께 바뀐다.
   */
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: getGetCandidatesQueryKey(auctionItemId),
      }),
      queryClient.invalidateQueries({ queryKey: getGetDealsQueryKey() }),
    ])

  const onSettleError = (error: unknown) => {
    const { title, description } = toDealErrorMessage(error)
    toast.error(title, { description })
    setPendingAction(null)
  }

  // 처리 대상은 항상 지금 차례인 후보다. 갱신 뒤에는 바뀌므로 미리 잡아 둔다.
  const partnerName = current?.nickname ?? null

  const completeMutation = useComplete({
    mutation: {
      onSuccess: async () => {
        await refresh()
        setPendingAction(null)
        toast.success('거래를 성사로 확정했어요', {
          description: partnerName
            ? `${partnerName}${josa(partnerName, '과', '와')}의 거래가 완료로 기록됐어요.`
            : undefined,
        })
      },
      onError: onSettleError,
    },
  })

  const failMutation = useFail({
    mutation: {
      onSuccess: async () => {
        await refresh()
        setPendingAction(null)
        toast.success('거래 실패로 처리했어요', {
          description: '차순위 후보가 있으면 낙찰 권한이 넘어갑니다.',
        })
      },
      onError: onSettleError,
    },
  })

  const settling = completeMutation.isPending || failMutation.isPending

  const settle = (action: 'complete' | 'fail') => {
    if (!current) return

    const variables = {
      auctionItemId,
      candidateId: current.dealCandidateId,
    }

    if (action === 'complete') completeMutation.mutate(variables)
    else failMutation.mutate(variables)
  }

  if (candidatesQuery.isPending || itemQuery.isPending) return <RoutePending />

  const notice = toAccessNotice(candidatesQuery.error?.response?.status)
  if (notice) {
    return (
      <AppShell title="거래 상세" back>
        <EmptyState title={notice.title} description={notice.description} />
      </AppShell>
    )
  }

  if (candidatesQuery.isError) {
    return (
      <RouteError
        error={candidatesQuery.error}
        reset={() => void candidatesQuery.refetch()}
      />
    )
  }

  if (itemQuery.isError || !item) {
    return (
      <AppShell title="거래 상세" back>
        <EmptyState
          title="거래를 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 거래입니다."
        />
      </AppShell>
    )
  }

  // 역할은 서버가 판정한다. 화면 분기는 UX 일 뿐이고 실제 차단은 서버 몫이다.
  const isSeller = list.viewerRole === 'SELLER'
  const unsold = item.status === 'FAILED'
  const productName = item.productName ?? '이름 없는 물품'

  /** 전원 실패는 후보 전체가 이 페이지에 있을 때만 단정할 수 있다. */
  const allFailed =
    list.rows.length > 0 &&
    list.rows.length === list.totalElements &&
    list.rows.every((row) => row.dealStatus === 'FAILED')

  /** 물품 패널·순위 패널이 같이 쓰는 상태 알약. */
  const badge: { label: string; tone: Tone } = unsold
    ? { label: '유찰', tone: 'live' }
    : settled
      ? { label: '거래 완료', tone: 'success' }
      : current
        ? {
            label: `${current.candidateRank}순위 · ${CANDIDATE_META[current.dealStatus].label}`,
            tone: 'notice',
          }
        : allFailed
          ? { label: '후보가 모두 실패했어요', tone: 'muted' }
          : { label: '진행 중인 후보 없음', tone: 'muted' }

  return (
    <AppShell title="거래 상세" back className="max-w-[1280px]">
      {/* 제목 + 역할 배지 */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="hidden md:block">
          <h1 className="text-[28px] font-extrabold text-foreground">
            거래 상세
          </h1>
          <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
            {isSeller
              ? '물품 정보와 낙찰 후보를 한 화면에서 확인하고 거래를 처리하세요.'
              : '물품 정보와 최종 순위, 현재 거래 상태를 한 화면에서 확인하세요.'}
          </p>
        </div>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[400px_minmax(0,1fr)]">
        {/* 물품 패널 — 400×560 */}
        <Panel className="flex flex-col">
          <h2 className="shrink-0 text-[18px] font-extrabold text-foreground">
            {isSeller ? '판매 물품' : '참여 물품'}
          </h2>

          {/*
           * 구매자 패널은 아래에 물품 설명까지 들어가서 이미지를 160으로 줄였다.
           * 판매자 패널은 Figma 그대로 220.
           */}
          {/* 모바일에서도 어떤 물건인지 보여야 한다. 높이만 낮춘다. */}
          <ProductThumbnail
            name={productName}
            size={640}
            iconClassName="size-7"
            className={cn(
              'mt-4 flex shrink-0 items-center justify-center rounded-[18px] border border-brand-300 bg-brand-50 text-brand-500',
              'h-[180px]',
              isSeller ? 'md:h-[220px]' : 'md:h-[160px]',
            )}
          />

          <p className="mt-6 shrink-0 text-[20px] font-extrabold text-foreground">
            {productName}
          </p>

          {/* 구매자는 상품명 바로 아래에 링크를 붙인다 (물품 상세와 같은 모양) */}
          {!isSeller &&
            (item.referenceUrl ? (
              <a
                href={item.referenceUrl}
                target="_blank"
                rel="noreferrer"
                className="mt-2 shrink-0 text-[13px] font-semibold text-brand-500 hover:underline"
              >
                상품 링크 ↗
              </a>
            ) : null)}

          <div
            className={cn(
              'shrink-0',
              isSeller ? 'mt-6 border-t pt-5' : 'mt-5 border-t pt-4',
            )}
          >
            {isSeller ? (
              <>
                <p className="text-[13px] font-semibold text-neutral-tertiary">
                  {unsold ? '시작가' : '현재 최고 입찰가'}
                </p>
                <p className="mt-2 text-[28px] font-extrabold tabular-nums text-foreground">
                  {formatWon(
                    (unsold
                      ? item.startingPrice
                      : (current?.bidAmount ?? item.currentPrice)) ?? 0,
                  )}
                </p>
              </>
            ) : (
              <>
                <p className="text-[12px] font-semibold text-neutral-tertiary">
                  판매자 정보
                </p>
                <div className="mt-2 flex items-baseline justify-between gap-3">
                  <p className="min-w-0 truncate text-[15px] font-bold text-foreground">
                    {deal?.partnerNickname ?? '—'}
                  </p>

                  {/*
                   * 연락처는 보조 정보다. 프로필 조회가 늦거나 실패해도 순위
                   * 패널은 그대로 보여야 하므로 값이 있을 때만 줄을 채운다.
                   */}
                  {sellerPhone && (
                    <a
                      href={`tel:${sellerPhone}`}
                      className="shrink-0 text-[14px] font-semibold text-brand-500 hover:underline"
                    >
                      {sellerPhone}
                    </a>
                  )}
                </div>
              </>
            )}
          </div>

          {isSeller ? (
            /*
             * 판매자는 이 물품의 현재 처리 단계를 알약으로 본다.
             * 예전에는 `-ml-4` 로 당겨 글자 시작선을 맞췄는데, 알약 배경이
             * 다른 줄보다 왼쪽으로 삐져나와 혼자 어긋나 보였다.
             */
            <span
              className={cn(
                'mt-5 flex h-[34px] w-fit shrink-0 items-center justify-center rounded-[17px] px-4 text-[12px] font-bold',
                TONE[badge.tone].surface,
                TONE[badge.tone].text,
              )}
            >
              {badge.label}
            </span>
          ) : (
            <>
              {/*
               * 물품 설명. Figma 에는 없고 남는 자리를 채우려고 넣었다.
               * 길면 이 블록 안에서만 스크롤한다 (패널은 560 에서 멈춘다).
               */}
              <div className="mt-5 flex min-h-0 flex-1 flex-col border-t pt-4">
                <p className="shrink-0 text-[12px] font-semibold text-neutral-tertiary">
                  물품 설명
                </p>
                <p className="mt-2 min-h-0 flex-1 overflow-y-auto text-[13px] leading-[1.6] font-medium whitespace-pre-line text-neutral-secondary">
                  {item.description?.trim()
                    ? item.description
                    : '등록된 설명이 없어요.'}
                </p>
              </div>
            </>
          )}
        </Panel>

        {/* 순위 패널 — 792×560 */}
        <Panel className="flex flex-col">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-[18px] font-extrabold text-foreground">
                {isSeller ? '낙찰 후보' : '최종 순위'}
              </h2>
              <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
                {unsold
                  ? '유효한 입찰자가 없어 거래를 진행할 수 없습니다.'
                  : isSeller
                    ? '현재 거래 후보와 다음 순위를 확인하세요.'
                    : '내 순위와 후보 승계 상태를 확인하세요.'}
              </p>
            </div>

            {/*
             * 판매자는 물품의 거래 단계를, 구매자는 자기 순위를 본다.
             * 구매자 쪽은 유찰이어도 순위 자리를 그대로 쓴다.
             */}
            <span
              className={cn(
                'flex h-[34px] shrink-0 items-center justify-center rounded-[17px] px-4 text-[12px] font-bold',
                isSeller
                  ? cn(TONE[badge.tone].surface, TONE[badge.tone].text)
                  : unsold
                    ? cn(TONE.live.surface, TONE.live.text)
                    : cn(TONE.brand.surface, TONE.brand.text),
              )}
            >
              {isSeller
                ? badge.label
                : unsold
                  ? '유찰'
                  : /* 내 순위는 다른 페이지에 있어도 서버가 알려준다. */
                    list.myRank != null
                    ? `내 순위 ${list.myRank}위`
                    : '순위 없음'}
            </span>
          </div>

          {unsold ? (
            <div className="mt-4 flex flex-1 flex-col items-center justify-center rounded-[20px] border bg-surface-subtle px-6 py-14 text-center">
              <p
                aria-hidden
                className="text-[34px] font-extrabold text-[#c4cbd2]"
              >
                —
              </p>
              <p className="mt-6 text-[20px] font-extrabold text-foreground">
                후보 없음
              </p>
              <p className="mt-3 text-[14px] font-medium whitespace-pre-line text-neutral-tertiary">
                {
                  '유효한 입찰이 없어 낙찰 후보가 없습니다.\n이 물품은 유찰 상태로 종료되었습니다.'
                }
              </p>
            </div>
          ) : (
            <>
              {/*
               * 모바일은 표 대신 후보 한 명당 카드 한 장으로 흐른다.
               * 6열 표를 375 폭에서 가로 스크롤시키면 쓰기 어렵다.
               */}
              {/*
               * 모바일(MOB-03)은 한 줄짜리 순위 행이 쌓이고, 지금 거래 중인
               * 후보만 강조 블록으로 커지면서 성사·실패 버튼을 연다.
               * 6열 표를 375 폭에서 가로 스크롤시키는 것보다 훨씬 낫다.
               */}
              {/*
               * 모바일은 후보 한 명이 한 줄이다. 예전에는 패널 안에 카드를
               * 한 겹 더 씌워서 좌우가 좁아지고 글자 크기도 제각각이었다.
               * 여기서는 줄만 쌓고, 지금 거래 중인 후보만 강조한다.
               */}
              <ul key={`m-${page}`} className="mt-4 space-y-2 md:hidden">
                {list.rows.map((candidate, index) => {
                  const active = candidate.dealStatus === 'IN_PROGRESS'
                  const mine = !isSeller && candidate.isMe
                  const result = toResultBadge(candidate, settled)
                  const tone: Tone = mine ? 'mine' : result.tone

                  return (
                    <li
                      key={candidate.dealCandidateId}
                      style={{ animationDelay: `${index * 30}ms` }}
                      className={cn(
                        'animate-rise rounded-2xl border px-3.5 py-3',
                        active
                          ? 'border-result-progress/40 bg-result-progress-surface'
                          : mine
                            ? 'border-brand-300 bg-brand-50'
                            : 'bg-card',
                      )}
                    >
                      <div className="flex items-center gap-2.5">
                        <span className="shrink-0 text-[12px] font-bold tabular-nums text-neutral-tertiary">
                          {candidate.candidateRank}순위
                        </span>
                        <span className="min-w-0 flex-1 truncate text-[14px] font-bold text-foreground">
                          {candidate.nickname}
                          {mine && (
                            <span className="ml-1 text-[11px] font-bold text-brand-600">
                              나
                            </span>
                          )}
                        </span>
                        <span className="shrink-0 text-[15px] font-extrabold tabular-nums text-foreground">
                          {formatWon(candidate.bidAmount)}
                        </span>
                      </div>

                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <span
                          className={cn(
                            'flex h-[22px] items-center rounded-full px-2 text-[11px] font-bold',
                            TONE[tone].surface,
                            TONE[tone].text,
                          )}
                        >
                          {result.label}
                        </span>

                        {/* 서버가 거래 상대일 때만 연락처를 내려준다. */}
                        {candidate.phoneNumber && (
                          <a
                            href={`tel:${candidate.phoneNumber}`}
                            className="text-[12px] font-semibold text-brand-500 hover:underline"
                          >
                            {candidate.phoneNumber}
                          </a>
                        )}

                        {isSeller && active && (
                          <span className="ml-auto flex gap-1.5">
                            <button
                              type="button"
                              disabled={settling}
                              onClick={() => setPendingAction('complete')}
                              className="ease-soft h-8 rounded-lg bg-brand-500 px-2.5 text-[12px] font-bold text-white transition-all duration-150 active:scale-95 disabled:opacity-50"
                            >
                              거래 성사
                            </button>
                            <button
                              type="button"
                              disabled={settling}
                              onClick={() => setPendingAction('fail')}
                              className="ease-soft h-8 rounded-lg border border-live/50 bg-card px-2.5 text-[12px] font-bold text-live transition-all duration-150 active:scale-95 disabled:opacity-50"
                            >
                              거래 실패
                            </button>
                          </span>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>

              {/* 열 폭이 좁아지면 표가 깨지므로 가로 스크롤로 흘린다. */}
              <div className="mt-4 hidden min-h-0 overflow-x-auto md:block">
                <div className={isSeller ? 'min-w-[680px]' : 'min-w-[560px]'}>
                  <div
                    className={cn(
                      'grid h-10 items-center rounded-[12px] bg-[#f4f7fc] pr-6 pl-4 text-[12px] font-semibold text-neutral-tertiary',
                      isSeller ? SELLER_COLS : BUYER_COLS,
                    )}
                  >
                    <span>순위</span>
                    <span>입찰자</span>
                    {isSeller && <span>연락처</span>}
                    <span>입찰가</span>
                    <span className="text-center">
                      {isSeller ? '거래 상태' : '결과'}
                    </span>
                    {isSeller && <span className="text-center">처리</span>}
                  </div>

                  {/* key 로 remount 시켜 페이지를 넘길 때마다 행이 다시 올라오게 한다. */}
                  <ul key={page} className="mt-3 space-y-3.5">
                    {list.rows.map((candidate, index) => {
                      const active = candidate.dealStatus === 'IN_PROGRESS'
                      // "나" 표시는 구매자 화면에만 있다. 판매자는 목록 밖이다.
                      const mine = !isSeller && candidate.isMe

                      // 구매자 화면은 "결과", 판매자 화면은 "거래 상태" 열이다.
                      const result = toResultBadge(candidate, settled)
                      const resultTone: Tone = mine ? 'mine' : result.tone

                      return (
                        <li
                          key={candidate.dealCandidateId}
                          style={{ animationDelay: `${index * 30}ms` }}
                          className={cn(
                            'animate-rise grid h-[50px] items-center rounded-[14px] border pr-6 pl-4',
                            isSeller ? SELLER_COLS : BUYER_COLS,
                            active
                              ? 'border-[#ffe6b0] bg-[#fffcf4]'
                              : mine
                                ? 'border-brand-300 bg-brand-50'
                                : 'bg-card',
                          )}
                        >
                          <span
                            className={cn(
                              'flex size-7 items-center justify-center rounded-[14px] text-[11px] font-extrabold',
                              active
                                ? 'bg-result-progress-surface text-result-progress'
                                : mine
                                  ? 'bg-brand-500 text-white'
                                  : 'bg-brand-50 text-brand-500',
                            )}
                          >
                            {candidate.candidateRank}
                          </span>

                          <span className="min-w-0 truncate text-[14px] font-bold text-foreground">
                            {candidate.nickname}
                            {mine && ' (나)'}
                          </span>

                          {/*
                           * 연락처를 보여줄지 화면이 계산하지 않는다. 서버가
                           * 거래 상대인 후보에게만 값을 내려준다.
                           */}
                          {isSeller &&
                            (candidate.phoneNumber ? (
                              <a
                                href={`tel:${candidate.phoneNumber}`}
                                className="truncate text-[13px] font-bold text-brand-500 hover:underline"
                              >
                                {candidate.phoneNumber}
                              </a>
                            ) : (
                              <span className="text-[13px] font-medium text-neutral-tertiary">
                                —
                              </span>
                            ))}

                          <span className="text-[14px] font-extrabold tabular-nums text-foreground">
                            {formatWon(candidate.bidAmount)}
                          </span>

                          <span
                            className={cn(
                              'mx-auto flex h-8 w-full items-center justify-center rounded-2xl px-2 text-[12px] font-bold',
                              TONE[resultTone].surface,
                              TONE[resultTone].text,
                            )}
                          >
                            {mine ? `내 순위 · ${result.label}` : result.label}
                          </span>

                          {isSeller &&
                            (active ? (
                              <span className="flex items-center justify-center gap-2">
                                <button
                                  type="button"
                                  disabled={settling}
                                  onClick={() => setPendingAction('fail')}
                                  className="ease-soft h-8 w-16 rounded-2xl border border-[#ffc0c3] bg-card text-[12px] font-bold text-live transition-all duration-150 hover:bg-[#fff5f5] active:scale-95 disabled:opacity-50"
                                >
                                  실패
                                </button>
                                <button
                                  type="button"
                                  disabled={settling}
                                  onClick={() => setPendingAction('complete')}
                                  className="ease-soft h-8 w-[68px] rounded-2xl bg-brand-500 text-[12px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 disabled:opacity-50"
                                >
                                  성사
                                </button>
                              </span>
                            ) : (
                              <span className="text-center text-[14px] font-medium text-neutral-muted">
                                —
                              </span>
                            ))}
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>

              <Pager
                page={page}
                pageCount={Math.max(1, list.totalPages)}
                onChange={setPage}
                meta={`총 ${list.totalElements}명 · ${CANDIDATE_PAGE_SIZE}명씩`}
                className="mt-auto pt-6"
              />
            </>
          )}
        </Panel>
      </div>

      <ConfirmDialog
        open={pendingAction !== null}
        tone={pendingAction === 'fail' ? 'danger' : 'brand'}
        title={
          pendingAction === 'complete'
            ? '거래를 성사로 확정할까요?'
            : '거래를 실패로 처리할까요?'
        }
        description={
          pendingAction === 'complete'
            ? `${partnerName ?? '현재 후보'} 님과의 거래가 완료된 것으로 기록됩니다. 되돌릴 수 없어요.`
            : `${partnerName ?? '현재 후보'} 님과의 거래가 실패로 기록되고, 차순위 후보에게 기회가 넘어갑니다.`
        }
        confirmLabel={pendingAction === 'complete' ? '거래 성사' : '거래 실패'}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => pendingAction && settle(pendingAction)}
      />
    </AppShell>
  )
}

/*
 * 표 열 폭. Figma 값(순위 28 / 입찰자 110 / 연락처 140 / 입찰가 90 /
 * 상태 116 / 처리 156)에 뒤쪽 여백을 더해 한 칸으로 묶었다.
 */
const SELLER_COLS = 'grid-cols-[48px_minmax(110px,1fr)_150px_100px_124px_156px]'
const BUYER_COLS = 'grid-cols-[84px_minmax(150px,1fr)_210px_166px]'

/**
 * Figma 패널 — 반경 24, 1px 보더, 안쪽 여백 28, 높이 560.
 *
 * 높이를 `min-h` 가 아니라 `h` 로 못박는다. 물품 설명처럼 안에서만 스크롤하는
 * 블록이 줄어들 기준 높이가 있어야 하기 때문이다.
 */
function Panel({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <section
      className={cn(
        'rounded-[24px] border bg-card p-7 lg:h-[calc(100svh-13rem)] lg:max-h-[720px] lg:min-h-[560px]',
        className,
      )}
    >
      {children}
    </section>
  )
}
