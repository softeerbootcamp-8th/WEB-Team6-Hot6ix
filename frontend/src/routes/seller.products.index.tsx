import { createFileRoute, Link } from '@tanstack/react-router'
import { ImageIcon, Search } from 'lucide-react'
import { useMemo, useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { Input } from '@/components/ui/input'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import type { ProductStatus } from '@/mocks/types'

export const Route = createFileRoute('/seller/products/')({
  beforeLoad: requireMember,
  component: SellerProductsPage,
})

const STATUS_META: Record<
  ProductStatus,
  { label: string; tone: 'neutral' | 'brand' | 'success' }
> = {
  DRAFT: { label: '등록됨', tone: 'neutral' },
  IN_AUCTION: { label: '경매 중', tone: 'brand' },
  SOLD: { label: '판매 완료', tone: 'success' },
}

type Filter = 'ALL' | ProductStatus

function SellerProductsPage() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [keyword, setKeyword] = useState('')

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

  const FILTERS: { key: Filter; label: string }[] = [
    { key: 'ALL', label: `전체 ${products.length}` },
    {
      key: 'DRAFT',
      label: `등록됨 ${products.filter((p) => p.status === 'DRAFT').length}`,
    },
    {
      key: 'IN_AUCTION',
      label: `경매 중 ${products.filter((p) => p.status === 'IN_AUCTION').length}`,
    },
    {
      key: 'SOLD',
      label: `판매 완료 ${products.filter((p) => p.status === 'SOLD').length}`,
    },
  ]

  return (
    <AppShell title="상품 관리" back>
      <PageHeader
        title="상품 관리"
        description="등록한 상품을 경매방 물품으로 편성할 수 있어요."
        actions={
          <Link
            to="/seller/products/new"
            className="rounded-lg bg-primary px-4 py-2 text-label font-bold text-primary-foreground transition-opacity hover:opacity-90"
          >
            + 상품 등록
          </Link>
        }
      />

      {products.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            icon={<ImageIcon className="size-8" />}
            title="등록한 상품이 없어요"
            description="상품을 먼저 등록하면 경매방에 물품으로 넣을 수 있어요."
            action={
              <Link
                to="/seller/products/new"
                className="inline-block rounded-2xl bg-primary px-6 py-3.5 text-card-title font-bold text-primary-foreground transition-opacity hover:opacity-90"
              >
                상품 등록하기
              </Link>
            }
          />
        </div>
      ) : (
        <>
          <div className="mt-6 flex flex-wrap items-center gap-2 rounded-2xl border bg-card p-3">
            <div
              role="tablist"
              aria-label="상품 상태 필터"
              className="flex flex-wrap gap-1"
            >
              {FILTERS.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  role="tab"
                  aria-selected={filter === item.key}
                  onClick={() => setFilter(item.key)}
                  className={cn(
                    'rounded-lg px-3.5 py-1.5 text-label font-bold transition-colors',
                    filter === item.key
                      ? 'bg-brand-50 text-brand-600'
                      : 'font-medium text-neutral-tertiary hover:bg-fill',
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>

            <div className="relative ml-auto w-full sm:w-[240px]">
              <Search
                aria-hidden
                className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-neutral-muted"
              />
              <Input
                type="search"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="상품명 검색"
                aria-label="상품명 검색"
                className="h-10 pl-9 text-label font-normal"
              />
            </div>
          </div>

          {visible.length === 0 ? (
            <div className="mt-4">
              <EmptyState
                title="조건에 맞는 상품이 없어요"
                description="필터나 검색어를 바꿔보세요."
              />
            </div>
          ) : (
            <ul className="mt-4 grid gap-3 lg:grid-cols-2">
              {visible.map((product) => {
                const meta = STATUS_META[product.status]
                return (
                  <li
                    key={product.id}
                    className="flex gap-4 rounded-3xl border bg-card p-4"
                  >
                    <span
                      aria-hidden
                      className="flex size-[92px] shrink-0 items-center justify-center rounded-2xl bg-fill text-neutral-muted"
                    >
                      <ImageIcon className="size-5" />
                    </span>

                    <div className="flex min-w-0 flex-1 flex-col">
                      <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
                      <h3 className="mt-2 truncate text-card-title font-bold text-foreground">
                        {product.name}
                      </h3>
                      <p className="mt-1 truncate text-caption font-normal text-neutral-tertiary">
                        {product.category} · {product.description}
                      </p>

                      <div className="mt-auto flex items-center justify-between pt-3">
                        <span className="text-caption font-normal text-neutral-muted">
                          {formatDate(product.createdAt)} 등록
                        </span>
                        <Link
                          to="/seller/products/$productId/edit"
                          params={{ productId: String(product.id) }}
                          className="rounded-lg border px-4 py-1.5 text-label font-semibold text-neutral-secondary transition-colors hover:border-border-strong"
                        >
                          수정
                        </Link>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </>
      )}
    </AppShell>
  )
}
