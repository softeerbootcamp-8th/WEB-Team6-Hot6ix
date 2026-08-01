import { createFileRoute, Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { ImageIcon, Search } from 'lucide-react'
import { useMemo, useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Dropdown } from '@/components/ui/dropdown'
import { EmptyState } from '@/components/page-header'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { Pager } from '@/components/pager'
import { PRODUCT_RESULT } from '@/features/seller/product-status'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import type { ProductStatus } from '@/mocks/types'

/**
 * 상품 관리 (Figma `WEB-05 · 판매자 · 상품 관리`, 713:3785).
 *
 * 1216 폭 한 덩어리다. 검색·결과 필터·총 개수를 담은 64 높이 툴바 아래
 * 표 카드가 오고, 5줄씩 끊어 보여준 뒤 아래에 페이지네이션이 붙는다.
 */
export const Route = createFileRoute('/seller/products/')({
  beforeLoad: requireMember,
  component: SellerProductsPage,
})

/** Figma 표 카드에 그려진 줄 수. */
const PAGE_SIZE = 5

const TABLE_COLS = 'grid-cols-[56px_minmax(0,1fr)_180px_208px_116px] gap-x-5'

const FILTERS: { key: 'ALL' | ProductStatus; label: string }[] = [
  { key: 'ALL', label: '전체 결과' },
  { key: 'DRAFT', label: '미진행' },
  { key: 'IN_AUCTION', label: '경매 중' },
  { key: 'SOLD', label: '낙찰' },
  { key: 'UNSOLD', label: '유찰' },
]

function SellerProductsPage() {
  const [filter, setFilter] = useState<'ALL' | ProductStatus>('ALL')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  const products = MOCK_PRODUCTS

  const visible = useMemo(() => {
    const byStatus =
      filter === 'ALL'
        ? products
        : products.filter((product) => product.status === filter)
    const trimmed = keyword.trim()
    if (!trimmed) return byStatus
    return byStatus.filter((product) => product.name.includes(trimmed))
  }, [products, filter, keyword])

  const pageCount = Math.max(1, Math.ceil(visible.length / PAGE_SIZE))
  // 필터·검색으로 목록이 줄면 현재 페이지가 범위를 벗어날 수 있다.
  const safePage = Math.min(page, pageCount - 1)
  const rows = visible.slice(
    safePage * PAGE_SIZE,
    safePage * PAGE_SIZE + PAGE_SIZE,
  )

  return (
    <AppShell title="상품 관리" back className="max-w-[1280px]">
      {/* 모바일(MOB-05)은 제목 없이 전체 폭 등록 버튼부터 시작한다. */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="hidden md:block">
          <h1 className="text-[28px] font-extrabold text-foreground">
            상품 관리
          </h1>
          <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
            경매에 사용할 상품과 경매 결과를 관리하세요.
          </p>
        </div>

        <Link
          to="/seller/products/new"
          className="ease-soft flex h-11 w-full shrink-0 items-center justify-center rounded-[14px] bg-brand-500 text-[15px] font-medium text-white transition-all duration-150 hover:opacity-90 active:scale-95 md:h-[52px] md:w-40 md:font-bold"
        >
          + 상품 등록
        </Link>
      </div>

      {products.length === 0 ? (
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
                onChange={(event) => {
                  setKeyword(event.target.value)
                  setPage(0)
                }}
                placeholder="상품명으로 검색"
                aria-label="상품명으로 검색"
                className="h-10 w-full rounded-xl border bg-surface-subtle pr-4 pl-10 text-[14px] font-medium outline-none placeholder:text-neutral-muted focus-visible:border-brand-400"
              />
            </div>

            <Dropdown
              label="경매 결과 필터"
              value={filter}
              options={FILTERS.map((item) => ({
                value: item.key,
                label: item.label,
              }))}
              onChange={(next) => {
                setFilter(next)
                setPage(0)
              }}
              className="w-32 shrink-0 md:w-40"
            />

            <p className="hidden w-[120px] shrink-0 text-right text-[13px] font-bold text-brand-500 md:block">
              총 {visible.length}개
            </p>
          </div>

          <p className="mt-5 text-[13px] font-medium text-neutral-muted md:hidden">
            총 {visible.length}개 · {PAGE_SIZE}개씩
          </p>

          {visible.length === 0 ? (
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
              <ul key={`m-${safePage}`} className="mt-2 space-y-3 md:hidden">
                {rows.map((product, index) => {
                  const result = PRODUCT_RESULT[product.status]
                  const editable = product.status === 'DRAFT'

                  return (
                    <li key={product.id}>
                      {/* 상품을 누르면 그 상품의 상세로 간다 (거래 목록 아님). */}
                      <Link
                        to="/seller/products/$productId"
                        params={{ productId: String(product.id) }}
                        style={{ animationDelay: `${index * 30}ms` }}
                        className="animate-rise ease-soft flex items-center gap-4 rounded-2xl border bg-card p-3.5 transition-all duration-150 active:scale-[0.99]"
                      >
                        <ProductThumbnail
                          name={product.name}
                          size={200}
                          iconClassName="size-6"
                          className="flex size-16 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-500"
                        />

                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-[15px] font-bold text-foreground">
                            {product.name}
                          </span>
                          <span className="mt-1 block truncate text-[12px] font-medium text-neutral-tertiary">
                            {product.category} · {formatDate(product.createdAt)}
                          </span>
                          <span
                            className={cn(
                              'mt-2 flex h-6 w-[72px] items-center justify-center rounded-full text-[11px] font-bold',
                              result.className,
                            )}
                          >
                            {result.label}
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
                    <span>경매 결과</span>
                    <span className="text-center">관리</span>
                  </div>

                  {/* key 로 remount 시켜 페이지를 넘길 때마다 행이 다시 올라온다. */}
                  {/* 행이 붙어 보이지 않도록 높이와 간격을 함께 키운다. */}
                  <ul key={safePage} className="space-y-1 p-3">
                    {rows.map((product, index) => {
                      const result = PRODUCT_RESULT[product.status]
                      // 경매를 거친 상품은 수정할 수 없다. 결과만 볼 수 있다.
                      const editable = product.status === 'DRAFT'

                      return (
                        <li
                          key={product.id}
                          style={{ animationDelay: `${index * 30}ms` }}
                          className={cn(
                            'animate-rise grid h-[84px] items-center rounded-2xl pr-5 pl-4 transition-colors duration-150 hover:bg-surface-subtle',
                            TABLE_COLS,
                          )}
                        >
                          <ProductThumbnail
                            name={product.name}
                            size={200}
                            iconClassName="size-6"
                            className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                          />

                          <span className="min-w-0">
                            <Link
                              to="/seller/products/$productId"
                              params={{ productId: String(product.id) }}
                              className="block truncate text-[15px] font-bold text-foreground hover:text-brand-500 hover:underline"
                            >
                              {product.name}
                            </Link>
                            <span className="mt-1 block truncate text-[13px] font-medium text-neutral-tertiary">
                              {product.category}
                            </span>
                          </span>

                          <span className="text-[13px] font-medium tabular-nums text-neutral-tertiary">
                            {formatDate(product.createdAt)}
                          </span>

                          <span
                            className={cn(
                              'flex h-8 w-28 items-center justify-center rounded-2xl text-[12px] font-bold',
                              result.className,
                            )}
                          >
                            {result.label}
                          </span>

                          <Link
                            to={
                              editable
                                ? '/seller/products/$productId/edit'
                                : '/seller/products/$productId'
                            }
                            params={{ productId: String(product.id) }}
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

              <p className="mt-5 text-center text-[12px] font-medium text-neutral-tertiary md:hidden">
                상품은 한 번의 경매에만 사용할 수 있어요.
              </p>

              {/* 페이지 넘김은 언제나 목록 맨 아래 */}
              <Pager
                page={safePage}
                pageCount={pageCount}
                onChange={setPage}
                size={40}
                className="mt-5 md:mt-8"
              />
            </>
          )}
        </>
      )}
    </AppShell>
  )
}
