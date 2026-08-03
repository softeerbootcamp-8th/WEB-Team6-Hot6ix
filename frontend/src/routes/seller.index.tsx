import { createFileRoute, Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { ProfilePhoto } from '@/components/profile-photo'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_TRADES } from '@/mocks/data'
import { PRODUCT_STATUS } from '@/features/seller/product-status'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { RouteError, RoutePending } from '@/components/route-states'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'
import { useProductList } from '@/features/seller/use-product-list'

/**
 * 판매자 정보 진입.
 *
 * - 프로필 있음: `WEB-02 · 판매자 · 판매자 정보 진입` (713:4052)
 * - 프로필 없음: `WEB-01 · 판매자 · 프로필 미등록`   (847:12938)
 *
 * 프로필 있음 화면은 380 프로필 패널 + 24 간격 + 812 상품 현황 패널, 높이 560.
 */
export const Route = createFileRoute('/seller/')({
  beforeLoad: requireMember,
  component: SellerHomePage,
})

/** 상품 현황 표에 미리 보여주는 줄 수. 나머지는 "전체 상품 보기"로 넘긴다. */
const PREVIEW_COUNT = 4

const TABLE_COLS = 'grid-cols-[56px_minmax(0,1fr)_204px_120px] gap-x-5'

function SellerHomePage() {
  const { profile, isPending, notFound, isError, error, refetch } =
    useMySellerProfile()
  // 미리보기라 첫 쪽만 받는다. 서버가 전체 개수를 주지 않아 더 있으면 "4+"로 적는다.
  const products = useProductList({ size: PREVIEW_COUNT })

  if (isPending) return <RoutePending />
  if (isError && error)
    return <RouteError error={error} reset={() => void refetch()} />

  // 404 는 장애가 아니라 "아직 등록하지 않았다"는 정상 상태다.
  if (notFound || !profile) return <MissingProfile />

  // 완료 거래 수는 아직 목업이다 (거래 화면 소관).
  const completedTrades = MOCK_TRADES.filter(
    (trade) => trade.role === 'SELLER' && trade.status === 'COMPLETED',
  ).length

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

        <Link
          to="/seller/rooms/new"
          className="ease-soft flex h-11 w-full shrink-0 items-center justify-center rounded-[14px] bg-brand-500 md:h-9 md:w-[148px] text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95"
        >
          + 경매방 만들기
        </Link>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
        {/* 판매자 프로필 — 380×560 */}
        <section className="flex flex-col rounded-[20px] border bg-card p-7 lg:h-[calc(100svh-14rem)] lg:min-h-[560px]">
          <h2 className="text-[18px] font-extrabold text-foreground">
            판매자 프로필
          </h2>

          {/* Figma 아바타 자리(160). 서버에 사진이 없으면 목업 사진을 넣는다. */}
          <ProfilePhoto
            seed={profile.storeName ?? ''}
            src={profile.storeImageUrl}
            size={400}
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
                value: products.isPending
                  ? '—'
                  : `${products.products.length}${products.hasNextPage ? '+' : ''}`,
                accent: 'text-foreground',
              },
              {
                label: '완료 거래',
                value: completedTrades,
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
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 className="text-[18px] font-extrabold text-foreground">
                상품 현황
              </h2>
              <p className="mt-2.5 text-[13px] font-medium text-neutral-tertiary">
                상품은 한 번의 경매에만 사용할 수 있어요.
              </p>
            </div>

            {/* 목록 맨 아래가 아니라 섹션 오른쪽 위에 둔다. */}
            <Link
              to="/seller/products"
              className="ease-soft shrink-0 rounded-lg px-1 py-0.5 text-[13px] font-bold text-brand-500 transition-colors duration-150 hover:bg-brand-50"
            >
              전체 상품 보기 →
            </Link>
          </div>

          {products.isPending ? (
            <ul aria-hidden className="mt-6 space-y-2">
              {Array.from({ length: PREVIEW_COUNT }).map((_, index) => (
                <li
                  key={index}
                  className="animate-skeleton h-[76px] rounded-2xl bg-fill"
                />
              ))}
            </ul>
          ) : products.isError ? (
            <p className="mt-6 text-[13px] font-medium text-neutral-tertiary">
              상품을 불러오지 못했어요. 전체 상품 보기에서 다시 시도해 주세요.
            </p>
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
              {/* 모바일은 표 대신 행 카드로 흐른다 (좁은 화면 가로 스크롤 방지) */}
              <ul className="mt-4 space-y-3 md:hidden">
                {products.products.map((product) => {
                  const status =
                    PRODUCT_STATUS[product.status ?? 'UNREGISTERED']

                  return (
                    <li key={product.productId}>
                      {/* 글자 규격은 상품 관리 화면과 같게 둔다. */}
                      <Link
                        to="/seller/products/$productId"
                        params={{ productId: String(product.productId) }}
                        className="ease-soft flex items-center gap-4 rounded-2xl border bg-card p-3.5 transition-all duration-150 active:scale-[0.99]"
                      >
                        <ProductThumbnail
                          name={product.name ?? ''}
                          src={product.imageUrl}
                          size={200}
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
                          상세 →
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>

              <div className="mt-6 hidden min-h-0 overflow-x-auto md:block">
                <div className="min-w-[560px]">
                  <div
                    className={cn(
                      'grid h-11 items-center rounded-[14px] bg-surface-subtle pr-9 pl-5 text-[13px] font-bold text-neutral-tertiary',
                      TABLE_COLS,
                    )}
                  >
                    <span>상품</span>
                    <span />
                    <span>등록일</span>
                    <span>경매 상태</span>
                  </div>

                  <ul className="mt-3 space-y-1">
                    {products.products.map((product, index) => {
                      const status =
                        PRODUCT_STATUS[product.status ?? 'UNREGISTERED']

                      return (
                        <li
                          key={product.productId}
                          style={{ animationDelay: `${index * 30}ms` }}
                          className={cn(
                            'animate-rise grid h-[76px] items-center rounded-2xl pr-9 pl-5 transition-colors duration-150 hover:bg-surface-subtle',
                            TABLE_COLS,
                          )}
                        >
                          <ProductThumbnail
                            name={product.name ?? ''}
                            src={product.imageUrl}
                            size={200}
                            iconClassName="size-6"
                            className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                          />

                          <span className="min-w-0">
                            <Link
                              to="/seller/products/$productId"
                              params={{ productId: String(product.productId) }}
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
                              'flex h-8 w-[120px] items-center justify-center rounded-2xl text-[12px] font-bold',
                              status.className,
                            )}
                          >
                            {status.label}
                          </span>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>
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
