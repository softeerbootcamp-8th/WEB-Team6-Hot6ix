import { createFileRoute, Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { ImageIcon, Search } from 'lucide-react'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { Dropdown } from '@/components/ui/dropdown'
import { EmptyState } from '@/components/page-header'
import {
  PRODUCT_STATUS,
  canEditProduct,
} from '@/features/seller/product-status'
import { RouteError, RoutePending } from '@/components/route-states'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useProductList } from '@/features/seller/use-product-list'
import type { GetListStatus } from '@/api/generated/model'

/**
 * 상품 관리 (Figma `WEB-05 · 판매자 · 상품 관리`, 713:3785).
 *
 * 1216 폭 한 덩어리다. 검색·상태 필터를 담은 64 높이 툴바 아래 표 카드가 온다.
 *
 * 서버가 커서 페이지네이션이라 전체 개수를 주지 않는다. 그래서 Figma 의 번호
 * 페이지네이션과 "총 N개" 대신 맨 아래 "더 보기"로 이어 붙인다.
 */
export const Route = createFileRoute('/seller/products/')({
  beforeLoad: requireMember,
  component: SellerProductsPage,
})

/** 한 번에 받아 오는 줄 수. Figma 표 카드가 5줄이라 그 배수로 맞췄다. */
const PAGE_SIZE = 10

const TABLE_COLS = 'grid-cols-[56px_minmax(0,1fr)_180px_208px_116px] gap-x-5'

const FILTERS: { key: 'ALL' | GetListStatus; label: string }[] = [
  { key: 'ALL', label: '전체 상태' },
  { key: 'UNREGISTERED', label: '경매 미등록' },
  { key: 'READY', label: '경매 대기' },
  { key: 'IN_PROGRESS', label: '경매 중' },
  { key: 'ENDED', label: '경매 종료' },
]

function SellerProductsPage() {
  const [filter, setFilter] = useState<'ALL' | GetListStatus>('ALL')
  const [keyword, setKeyword] = useState('')
  // 검색어는 서버로 나가므로 한 글자마다 보내지 않는다.
  const debouncedKeyword = useDebouncedValue(keyword.trim())

  const {
    products,
    isPending,
    isError,
    error,
    refetch,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useProductList({
    keyword: debouncedKeyword || undefined,
    status: filter === 'ALL' ? undefined : filter,
    size: PAGE_SIZE,
  })

  /** 검색·필터를 걸지 않은 상태에서 비었으면 "등록한 상품이 없다"는 뜻이다. */
  const filtered = debouncedKeyword !== '' || filter !== 'ALL'

  if (isPending) return <RoutePending />
  if (isError && error)
    return <RouteError error={error} reset={() => void refetch()} />

  return (
    <AppShell title="상품 관리" back className="max-w-[1280px]">
      {/* 모바일(MOB-05)은 제목 없이 전체 폭 등록 버튼부터 시작한다. */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        {/* 판매자 정보·내 경매방과 같은 목록 화면 상단(20px 제목 + 13px 설명). */}
        <div className="hidden md:block">
          <h1 className="text-[20px] font-bold text-foreground">상품 관리</h1>
          <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
            경매에 사용할 상품과 경매 상태를 관리하세요.
          </p>
        </div>

        {/* 판매자 정보·내 경매방 화면의 상단 버튼과 같은 규격으로 맞춘다. */}
        <Link
          to="/seller/products/new"
          className="ease-soft flex h-11 w-full shrink-0 items-center justify-center rounded-[14px] bg-brand-500 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 md:h-9 md:w-[148px]"
        >
          + 상품 등록
        </Link>
      </div>

      {products.length === 0 && !filtered ? (
        <div className="mt-6">
          <EmptyState
            icon={<ImageIcon className="size-8" />}
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
        </div>
      ) : (
        <>
          {/* 툴바 — 웹 1216×64. 모바일은 검색만 남긴다(MOB-05). */}
          <div className="mt-4 flex flex-wrap items-center gap-3 rounded-[14px] border-0 bg-transparent p-0 md:border md:bg-card md:px-5 md:py-3.5">
            <div className="relative min-w-0 flex-1">
              <Search
                aria-hidden
                className="absolute top-1/2 left-4 size-4 -translate-y-1/2 text-neutral-muted"
              />
              <input
                type="search"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
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
              onChange={setFilter}
              className="w-32 shrink-0 md:w-40"
            />
          </div>

          {products.length === 0 ? (
            <div className="mt-4">
              <EmptyState
                title="조건에 맞는 상품이 없어요"
                description="필터나 검색어를 바꿔보세요."
              />
            </div>
          ) : (
            <>
              {/*
               * 모바일(MOB-05)은 표 대신 68 높이 행 카드다. 좁은 화면에서
               * 5열 표를 가로 스크롤시키는 것보다 훨씬 낫다.
               */}
              <ul className="mt-4 space-y-3 md:hidden">
                {products.map((product, index) => {
                  const status =
                    PRODUCT_STATUS[product.status ?? 'UNREGISTERED']
                  const editable = canEditProduct(product.status)

                  return (
                    <li key={product.productId}>
                      {/* 상품을 누르면 그 상품의 상세로 간다 (거래 목록 아님). */}
                      <Link
                        to="/seller/products/$productId"
                        params={{ productId: String(product.productId) }}
                        style={{
                          animationDelay: `${(index % PAGE_SIZE) * 30}ms`,
                        }}
                        className="animate-rise ease-soft flex items-center gap-4 rounded-2xl border bg-card p-3.5 transition-all duration-150 active:scale-[0.99]"
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

              <div className="mt-5 hidden overflow-x-auto rounded-[20px] border bg-card md:block">
                <div className="min-w-[860px]">
                  <div
                    className={cn(
                      'grid h-[52px] items-center rounded-t-[20px] bg-surface-subtle pr-8 pl-7 text-[13px] font-bold text-neutral-tertiary',
                      TABLE_COLS,
                    )}
                  >
                    <span>상품</span>
                    <span />
                    <span>등록일</span>
                    <span>경매 상태</span>
                    <span className="text-center">관리</span>
                  </div>

                  {/* 행이 붙어 보이지 않도록 높이와 간격을 함께 키운다. */}
                  <ul className="space-y-1 p-3">
                    {products.map((product, index) => {
                      const status =
                        PRODUCT_STATUS[product.status ?? 'UNREGISTERED']
                      // 경매가 시작된 상품은 수정할 수 없다. 상태만 볼 수 있다.
                      const editable = canEditProduct(product.status)

                      return (
                        <li
                          key={product.productId}
                          style={{
                            animationDelay: `${(index % PAGE_SIZE) * 30}ms`,
                          }}
                          className={cn(
                            'animate-rise grid h-[84px] items-center rounded-2xl pr-5 pl-4 transition-colors duration-150 hover:bg-surface-subtle',
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
                              'flex h-8 w-28 items-center justify-center rounded-2xl text-[12px] font-bold',
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
                            {editable ? '수정' : '상세 보기'}
                          </Link>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>

              <p className="mt-5 text-center text-[12px] font-medium break-keep text-neutral-tertiary md:hidden">
                상품은 한 번의 경매에만 사용할 수 있어요.
              </p>

              {/* 커서 페이지네이션이라 다음 쪽이 있을 때만 이어 붙인다. */}
              {hasNextPage && (
                <div className="mt-5 flex justify-center md:mt-8">
                  <Button
                    type="button"
                    variant="outline"
                    size="lg"
                    className="h-11 w-full rounded-xl md:w-40"
                    disabled={isFetchingNextPage}
                    onClick={() => void fetchNextPage()}
                  >
                    {isFetchingNextPage ? '불러오는 중…' : '더 보기'}
                  </Button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </AppShell>
  )
}
