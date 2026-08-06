import { createFileRoute, Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { ProfilePhoto } from '@/components/profile-photo'
import { Search } from 'lucide-react'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { Dropdown } from '@/components/ui/dropdown'
import { EmptyState, PageHeader } from '@/components/page-header'
import { Pager } from '@/components/pager'
import {
  PRODUCT_STATUS,
  canEditProduct,
} from '@/features/seller/product-status'
import { toDeals } from '@/features/trades/adapt-deal'
import { useGetDeals } from '@/api/generated/거래-내역/거래-내역'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { RouteError, RoutePending } from '@/components/route-states'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'
import { useProductList } from '@/features/seller/use-product-list'
import type { GetListStatus } from '@/api/generated/model'

/**
 * 판매자 정보 진입.
 *
 * - 프로필 있음: `WEB-02 · 판매자 · 판매자 정보 진입` (713:4052)
 * - 프로필 없음: `WEB-01 · 판매자 · 프로필 미등록`   (847:12938)
 *
 * 프로필 있음 화면은 380 프로필 패널 + 24 간격 + 812 상품 현황 패널, 높이 560.
 *
 * 상품 현황 패널이 상품 관리 화면을 그대로 품는다 — 검색·상태 필터·페이지네이션이
 * 여기 있고, 따로 있던 `/seller/products` 화면은 없앴다.
 */
export const Route = createFileRoute('/seller/')({
  beforeLoad: requireMember,
  component: SellerHomePage,
})

/**
 * 한 쪽에 담는 줄 수.
 *
 * 패널 높이(560)에 툴바와 페이지네이션까지 들어가야 해서 4줄이다.
 * 이보다 늘리면 목록이 패널 안에서 스크롤된다.
 */
const PAGE_SIZE = 4

const TABLE_COLS = 'grid-cols-[56px_minmax(0,1fr)_160px_110px_104px] gap-x-4'

const FILTERS: { key: 'ALL' | GetListStatus; label: string }[] = [
  { key: 'ALL', label: '전체 상태' },
  { key: 'UNREGISTERED', label: '경매 미등록' },
  { key: 'READY', label: '경매 대기' },
  { key: 'IN_PROGRESS', label: '경매 중' },
  { key: 'ENDED', label: '경매 종료' },
]

function SellerHomePage() {
  const { profile, isPending, notFound, isError, error, refetch } =
    useMySellerProfile()

  const [filter, setFilter] = useState<'ALL' | GetListStatus>('ALL')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  // 검색어는 서버로 나가므로 한 글자마다 보내지 않는다.
  const debouncedKeyword = useDebouncedValue(keyword.trim())

  /**
   * 조건이 바뀌면 첫 쪽으로 되돌린다. 3쪽을 보다가 필터를 걸면 결과가 한 쪽뿐일 수
   * 있는데, 그때 page 가 3으로 남아 있으면 빈 화면이 뜬다.
   */
  const changeFilter = (next: 'ALL' | GetListStatus) => {
    setFilter(next)
    setPage(0)
  }

  const changeKeyword = (next: string) => {
    setKeyword(next)
    setPage(0)
  }

  const products = useProductList({
    keyword: debouncedKeyword || undefined,
    status: filter === 'ALL' ? undefined : filter,
    page,
    size: PAGE_SIZE,
  })

  /** 검색·필터를 걸지 않은 상태에서 비었으면 "등록한 상품이 없다"는 뜻이다. */
  const filtered = debouncedKeyword !== '' || filter !== 'ALL'

  /*
   * "등록 상품" 숫자는 검색·필터와 무관한 전체 개수다. 위 목록 쿼리의
   * `totalElements` 를 쓰면 필터를 걸 때마다 통계 숫자가 따라 변한다.
   * 개수만 필요하므로 한 줄만 받아온다.
   */
  const allProducts = useProductList({ page: 0, size: 1 })

  /**
   * 완료 거래 수. 거래 내역 API 는 페이지 없이 전량을 주고 역할·상태를 서버가 판정해
   * 내려주므로 여기서 세면 된다. 파라미터가 없어 거래 내역 화면과 쿼리 키가 같고,
   * 그래서 둘 중 먼저 연 쪽의 응답을 나눠 쓴다.
   */
  const deals = useGetDeals()
  const completedTrades = toDeals(deals.data?.data).filter(
    (deal) => deal.role === 'SELLER' && deal.status === 'COMPLETED',
  ).length

  if (isPending) return <RoutePending />
  if (isError && error)
    return <RouteError error={error} reset={() => void refetch()} />

  // 404 는 장애가 아니라 "아직 등록하지 않았다"는 정상 상태다.
  if (notFound || !profile) return <MissingProfile />

  return (
    <AppShell title="판매자 정보" className="max-w-[1280px]">
      <div className="flex flex-wrap items-start justify-between gap-4">
        {/* 거래 내역·내 경매방과 같은 상단(20px 제목 + 13px 설명). */}
        <div>
          <h1 className="text-[20px] font-bold text-foreground">판매자 정보</h1>
          <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
            판매자 프로필과 상품 현황을 확인하세요.
          </p>
        </div>

        {/*
          이 화면이 다루는 건 프로필과 상품뿐이라 주 액션도 상품 등록 하나다.
          경매방 만들기는 `/rooms` 상단에 있다 — 화면마다 액션 하나씩 둔다.
          크기·글자는 상품 관리·내 경매방 화면의 상단 버튼과 같은 규격이다
          (모바일 h-11 전체폭 / 데스크톱 h-9 · 148px · 13px bold).
        */}
        <Link
          to="/seller/products/new"
          className="ease-soft flex h-11 w-full shrink-0 items-center justify-center rounded-[14px] bg-brand-500 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 md:h-9 md:w-[148px]"
        >
          + 상품 등록
        </Link>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
        {/* 판매자 프로필 — 380×560 */}
        <section className="flex flex-col rounded-[20px] border bg-card p-7 lg:h-[calc(100svh-14rem)] lg:min-h-[560px]">
          <h2 className="text-[18px] font-extrabold text-foreground">
            판매자 프로필
          </h2>

          {/* Figma 아바타 자리(160). 서버에 사진이 없으면 사람 아이콘이 뜬다. */}
          <ProfilePhoto
            src={profile.storeImageUrl}
            className="mt-5 size-40 shrink-0 self-center rounded-full border border-brand-300 bg-brand-50"
          />

          <p className="mt-7 text-center text-[24px] font-extrabold text-foreground">
            {profile.storeName}
          </p>

          <p className="mt-5 text-center text-[14px] font-medium text-neutral-tertiary">
            {profile.storeDescription || '소개 미등록'}
          </p>

          <dl className="mt-8 grid grid-cols-2 gap-5">
            {[
              {
                label: '등록 상품',
                // 표에 보이는 줄 수가 아니라 서버가 센 전체 개수다.
                value: allProducts.isPending ? '—' : allProducts.totalElements,
                accent: 'text-foreground',
              },
              {
                label: '완료 거래',
                value: deals.isPending ? '—' : completedTrades,
                accent: 'text-brand-500',
              },
            ].map((stat) => (
              <div
                key={stat.label}
                className="h-[84px] rounded-2xl border bg-surface-subtle pt-4 text-center"
              >
                <dt className="text-[12px] font-semibold text-neutral-tertiary">
                  {stat.label}
                </dt>
                <dd
                  className={cn(
                    'mt-2 text-[22px] font-extrabold tabular-nums',
                    stat.accent,
                  )}
                >
                  {stat.value}
                </dd>
              </div>
            ))}
          </dl>

          {/* `mt-auto` 만 두면 남는 공간이 없을 때 위 카드에 딱 붙는다. */}
          <div className="mt-auto pt-6">
            <Link
              to="/seller/profile/edit"
              className="ease-soft flex h-10 w-full items-center justify-center rounded-[20px] border border-brand-300 bg-card text-[13px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-[0.98]"
            >
              판매자 프로필 수정
            </Link>
          </div>
        </section>

        {/* 상품 현황 — 812×560 */}
        <section className="flex flex-col rounded-[20px] border bg-card p-7 lg:h-[calc(100svh-14rem)] lg:min-h-[560px]">
          <h2 className="min-w-0 shrink-0 text-[18px] font-extrabold text-foreground">
            상품 현황
          </h2>

          {/*
            검색·상태 필터. 상품이 아예 없으면 걸 조건도 없어서 숨긴다.
            규격은 상품 관리 화면에서 쓰던 툴바와 같다.
          */}
          {!(
            !products.isPending &&
            !products.isError &&
            products.products.length === 0 &&
            !filtered
          ) && (
            <div className="mt-4 flex shrink-0 flex-wrap items-center gap-3">
              <div className="relative min-w-0 flex-1">
                <Search
                  aria-hidden
                  className="absolute top-1/2 left-4 size-4 -translate-y-1/2 text-neutral-muted"
                />
                <input
                  type="search"
                  value={keyword}
                  onChange={(event) => changeKeyword(event.target.value)}
                  placeholder="상품명으로 검색"
                  aria-label="상품명으로 검색"
                  className="h-10 w-full rounded-xl border bg-surface-subtle pr-4 pl-10 text-[14px] font-medium outline-none placeholder:text-neutral-muted focus-visible:border-brand-400"
                />
              </div>

              <Dropdown
                label="경매 상태 필터"
                value={filter}
                options={FILTERS.map((item) => ({
                  value: item.key,
                  label: item.label,
                }))}
                onChange={changeFilter}
                className="w-32 shrink-0 md:w-40"
              />
            </div>
          )}

          {products.isPending ? (
            <ul aria-hidden className="mt-4 space-y-2">
              {Array.from({ length: PAGE_SIZE }).map((_, index) => (
                <li
                  key={index}
                  className="animate-skeleton h-[76px] rounded-2xl bg-fill"
                />
              ))}
            </ul>
          ) : products.isError ? (
            <div className="mt-6">
              <p className="text-[13px] font-medium text-neutral-tertiary">
                상품을 불러오지 못했어요.
              </p>
              <Button
                variant="brandOutline"
                size="field"
                className="mt-3 w-[120px]"
                onClick={() => void products.refetch()}
              >
                다시 시도
              </Button>
            </div>
          ) : products.products.length === 0 && filtered ? (
            <div className="mt-4">
              <EmptyState
                title="조건에 맞는 상품이 없어요"
                description="필터나 검색어를 바꿔보세요."
              />
            </div>
          ) : products.products.length === 0 ? (
            <EmptyState
              title="등록한 상품이 없어요"
              description="상품을 먼저 등록하면 경매방에 물품으로 넣을 수 있어요."
              action={
                <Link
                  to="/seller/products/new"
                  className="ease-soft inline-block rounded-[14px] bg-brand-500 px-6 py-3.5 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95"
                >
                  상품 등록하기
                </Link>
              }
            />
          ) : (
            <>
              {/*
                목록만 패널 안에서 스크롤한다. 패널 높이가 560 으로 묶여 있어서
                툴바와 페이지네이션은 늘 보이고 줄만 흐르게 둔다.
              */}
              <div className="min-h-0 flex-1 md:overflow-y-auto">
                {/* 모바일은 표 대신 행 카드로 흐른다 (좁은 화면 가로 스크롤 방지) */}
                <ul className="mt-4 space-y-3 md:hidden">
                  {products.products.map((product) => {
                    const status =
                      PRODUCT_STATUS[product.status ?? 'UNREGISTERED']
                    // 경매가 시작된 상품은 수정할 수 없다. 상태만 볼 수 있다.
                    const editable = canEditProduct(product.status)

                    return (
                      <li key={product.productId}>
                        {/* 글자 규격은 상품 관리 화면과 같게 둔다. */}
                        <Link
                          to="/seller/products/$productId"
                          params={{ productId: String(product.productId) }}
                          className="ease-soft flex items-center gap-4 rounded-2xl border bg-card p-3.5 transition-all duration-150 active:scale-[0.99]"
                        >
                          <ProductThumbnail
                            src={product.imageUrl}
                            iconClassName="size-6"
                            className="flex size-16 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-500"
                          />

                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-[15px] font-bold text-foreground">
                              {product.name}
                            </span>
                            <span className="mt-1 block truncate text-[12px] font-medium text-neutral-tertiary">
                              {product.createdAt
                                ? formatDate(product.createdAt)
                                : '-'}
                            </span>
                            <span
                              className={cn(
                                'mt-2 flex h-6 w-[72px] items-center justify-center rounded-full text-[11px] font-bold',
                                status.className,
                              )}
                            >
                              {status.label}
                            </span>
                          </span>

                          <span className="shrink-0 text-[13px] font-bold text-brand-500">
                            {editable ? '수정 →' : '상세 →'}
                          </span>
                        </Link>
                      </li>
                    )
                  })}
                </ul>

                <div className="mt-4 hidden overflow-x-auto md:block">
                  <div className="min-w-[600px]">
                    <div
                      className={cn(
                        'grid h-11 items-center rounded-[14px] bg-surface-subtle pr-5 pl-4 text-[13px] font-bold text-neutral-tertiary',
                        TABLE_COLS,
                      )}
                    >
                      <span>상품</span>
                      <span />
                      <span>등록일</span>
                      <span>경매 상태</span>
                      <span className="text-center">관리</span>
                    </div>

                    <ul className="mt-3 space-y-1">
                      {products.products.map((product, index) => {
                        const status =
                          PRODUCT_STATUS[product.status ?? 'UNREGISTERED']
                        const editable = canEditProduct(product.status)

                        return (
                          <li
                            key={product.productId}
                            style={{ animationDelay: `${index * 30}ms` }}
                            className={cn(
                              'animate-rise grid h-[76px] items-center rounded-2xl pr-5 pl-4 transition-colors duration-150 hover:bg-surface-subtle',
                              TABLE_COLS,
                            )}
                          >
                            <ProductThumbnail
                              src={product.imageUrl}
                              iconClassName="size-6"
                              className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                            />

                            <span className="min-w-0">
                              <Link
                                to="/seller/products/$productId"
                                params={{
                                  productId: String(product.productId),
                                }}
                                className="block truncate text-[15px] font-bold text-foreground hover:text-brand-500 hover:underline"
                              >
                                {product.name}
                              </Link>
                            </span>

                            <span className="text-[13px] font-medium tabular-nums text-neutral-tertiary">
                              {product.createdAt
                                ? formatDate(product.createdAt)
                                : '-'}
                            </span>

                            <span
                              className={cn(
                                'flex h-8 w-[110px] items-center justify-center rounded-2xl text-[12px] font-bold',
                                status.className,
                              )}
                            >
                              {status.label}
                            </span>

                            <Link
                              to={
                                editable
                                  ? '/seller/products/$productId/edit'
                                  : '/seller/products/$productId'
                              }
                              params={{ productId: String(product.productId) }}
                              className={cn(
                                'ease-soft flex h-10 items-center justify-center rounded-xl border bg-card text-[13px] font-bold transition-all duration-150 active:scale-95',
                                editable
                                  ? 'text-brand-500 hover:bg-brand-50'
                                  : 'text-neutral-secondary hover:bg-fill',
                              )}
                            >
                              {editable ? '수정' : '상세'}
                            </Link>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                </div>
              </div>

              {/*
                쪽수는 서버가 필터를 적용한 뒤 세어 준 값이라 화면에서 다시 계산하지 않는다.
                결과가 한 쪽뿐일 때도 버튼 한 개는 남기므로 최소 1로 둔다.
              */}
              <Pager
                page={page}
                pageCount={Math.max(1, products.totalPages)}
                onChange={setPage}
                className="mt-4 shrink-0"
              />
            </>
          )}
        </section>
      </div>
    </AppShell>
  )
}

/** 판매자 프로필을 아직 만들지 않은 상태 (`WEB-01`). 840×500 카드 한 장. */
function MissingProfile() {
  return (
    <AppShell title="판매자 정보" className="max-w-[1280px]">
      <PageHeader
        title="판매자 정보"
        description="판매를 시작하기 위한 판매자 프로필을 관리하세요."
      />

      <section className="mx-auto mt-4 w-full max-w-[840px] rounded-[24px] border bg-card px-6 pt-12 pb-[34px] text-center md:px-10">
        <span
          aria-hidden
          className="mx-auto flex size-20 items-center justify-center rounded-[40px] bg-brand-50 text-[28px] font-extrabold text-brand-500"
        >
          !
        </span>

        <h2 className="mt-6 text-[24px] font-extrabold text-foreground">
          판매자 프로필이 필요해요
        </h2>
        <p className="mt-4 text-[14px] leading-[1.6] font-medium whitespace-pre-line text-neutral-tertiary">
          {'프로필을 등록해야 상품을 판매하고\n새 경매방을 생성할 수 있습니다.'}
        </p>

        <div className="mt-6 rounded-[18px] bg-brand-50 px-6 py-[18px]">
          <p className="text-[13px] font-bold text-brand-500">
            프로필 등록 후 이용 가능
          </p>
          <p className="mt-3 text-[13px] font-semibold text-foreground">
            상품 등록 · 경매방 생성 · 판매 내역 관리
          </p>
        </div>

        <Link
          to="/seller/profile/new"
          className="ease-soft mx-auto mt-[38px] flex h-14 w-full max-w-[360px] items-center justify-center rounded-[14px] bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
        >
          판매자 프로필 등록
        </Link>

        <p className="mt-5 text-[12px] font-medium text-neutral-muted">
          가게 이름과 연락처를 등록하면 바로 시작할 수 있어요.
        </p>
      </section>
    </AppShell>
  )
}
