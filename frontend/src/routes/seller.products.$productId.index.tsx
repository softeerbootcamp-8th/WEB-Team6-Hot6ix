import { createFileRoute, Link } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_PRODUCTS, MOCK_TRADES } from '@/mocks/data'
import { PRODUCT_RESULT } from '@/features/seller/product-status'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { cn } from '@/lib/utils'
import { formatDate, formatWon } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'

/**
 * 상품 상세.
 *
 * 목록에서 상품을 누르면 여기로 온다. 예전에는 경매를 거친 상품을 누르면
 * 거래 내역 목록으로 보냈는데, 어떤 상품을 눌러도 같은 곳으로 가서
 * "이 상품이 어떻게 됐는지"를 알 수 없었다.
 *
 * 상품 정보 + 경매 결과 + (결과가 있으면) 그 거래로 가는 길을 함께 둔다.
 */
export const Route = createFileRoute('/seller/products/$productId/')({
  beforeLoad: requireMember,
  component: SellerProductDetailPage,
})

function SellerProductDetailPage() {
  const { productId } = Route.useParams()

  const product = MOCK_PRODUCTS.find(
    (candidate) => String(candidate.id) === productId,
  )

  if (!product) {
    return (
      <AppShell title="상품 상세" back className="max-w-[1280px]">
        <EmptyState
          title="상품을 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 상품입니다."
        />
      </AppShell>
    )
  }

  const result = PRODUCT_RESULT[product.status]
  const editable = product.status === 'DRAFT'
  // 경매를 거친 상품은 거래가 남는다. `productId` 로 정확히 잇는다.
  const trade = MOCK_TRADES.find(
    (candidate) => candidate.productId === product.id,
  )
  /** 경매에 한 번이라도 올라간 상품. 거래 화면으로 갈 길을 열어둔다. */
  const traded = product.status !== 'DRAFT'

  return (
    <AppShell title="상품 상세" back className="max-w-[1280px]">
      <PageHeader
        title="상품 상세"
        description="등록한 상품 정보와 경매 결과를 확인하세요."
      />

      <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
        {/* 왼쪽 · 사진과 기본 정보 */}
        <section className="rounded-[20px] border bg-card p-6">
          <div className="relative">
            <ProductThumbnail
              name={product.name}
              size={720}
              iconClassName="size-10"
              className="flex aspect-square w-full items-center justify-center rounded-2xl bg-fill text-neutral-muted"
            />

            <span
              className={cn(
                'absolute top-3 left-3 flex h-7 items-center rounded-full px-3 text-[12px] font-bold shadow-sm',
                result.className,
              )}
            >
              {result.label}
            </span>
          </div>

          <h2 className="mt-5 text-[22px] leading-[1.35] font-extrabold text-foreground">
            {product.name}
          </h2>

          <dl className="mt-4 divide-y rounded-2xl border bg-surface-subtle px-4">
            <div className="flex items-center justify-between gap-3 py-3">
              <dt className="text-[12px] font-medium text-neutral-tertiary">
                분류
              </dt>
              <dd className="truncate text-[13px] font-bold text-foreground">
                {product.category}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-3 py-3">
              <dt className="text-[12px] font-medium text-neutral-tertiary">
                등록일
              </dt>
              <dd className="text-[13px] font-bold tabular-nums text-foreground">
                {formatDate(product.createdAt)}
              </dd>
            </div>
            {product.productUrl && (
              <div className="flex items-center justify-between gap-3 py-3">
                <dt className="text-[12px] font-medium text-neutral-tertiary">
                  참고 링크
                </dt>
                <dd className="min-w-0">
                  <a
                    href={`https://${product.productUrl.replace(/^https?:\/\//, '')}`}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="block truncate text-[13px] font-bold text-brand-500 hover:underline"
                  >
                    열기 ↗
                  </a>
                </dd>
              </div>
            )}
          </dl>
        </section>

        {/* 오른쪽 · 설명과 다음 할 일 */}
        <div className="flex flex-col gap-5">
          <section className="rounded-[20px] border bg-card p-6 md:p-7">
            <h2 className="text-[16px] font-bold text-foreground">상품 설명</h2>
            <p className="mt-3 text-[14px] leading-[1.7] font-medium whitespace-pre-line text-neutral-secondary">
              {product.description?.trim()
                ? product.description
                : '등록된 설명이 없어요.'}
            </p>
          </section>

          <section className="rounded-[20px] border bg-card p-6 md:p-7">
            <h2 className="text-[16px] font-bold text-foreground">
              {trade ? '경매 결과' : '경매 진행'}
            </h2>

            {trade ? (
              <>
                <dl className="mt-4 grid gap-3 sm:grid-cols-3">
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      경매방
                    </dt>
                    <dd className="mt-1 truncate text-[14px] font-bold text-foreground">
                      {trade.roomTitle}
                    </dd>
                  </div>
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      {product.status === 'SOLD' ? '낙찰가' : '결과'}
                    </dt>
                    <dd
                      className={cn(
                        'mt-1 text-[14px] font-extrabold tabular-nums',
                        product.status === 'SOLD'
                          ? 'text-brand-500'
                          : 'text-live',
                      )}
                    >
                      {product.status === 'SOLD'
                        ? formatWon(trade.amount)
                        : '유찰'}
                    </dd>
                  </div>
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      마감일
                    </dt>
                    <dd className="mt-1 text-[14px] font-bold tabular-nums text-foreground">
                      {formatDate(trade.closedAt)}
                    </dd>
                  </div>
                </dl>

                {/* 낙찰·거래 중인 상품은 여기서 바로 거래로 넘어간다. */}
                <div className="mt-5 flex flex-wrap gap-2">
                  <Link
                    to="/trades/$itemId"
                    params={{ itemId: String(trade.auctionItemId) }}
                    className="ease-soft flex h-11 items-center rounded-xl bg-brand-500 px-5 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95"
                  >
                    거래 상세 보기
                  </Link>
                </div>
              </>
            ) : (
              <>
                <p className="mt-3 text-[13px] leading-[1.7] font-medium text-neutral-tertiary">
                  {product.status === 'IN_AUCTION'
                    ? '지금 경매방에서 진행 중인 상품이에요. 결과가 확정되면 여기에 표시됩니다.'
                    : traded
                      ? '경매를 마친 상품이에요. 거래 내역에서 진행 상황을 확인할 수 있어요.'
                      : '아직 경매에 사용하지 않은 상품이에요. 경매방을 만들 때 물품으로 넣을 수 있어요.'}
                </p>
              </>
            )}
          </section>

          <section className="rounded-[20px] border bg-card p-6 md:p-7">
            <h2 className="text-[16px] font-bold text-foreground">상품 관리</h2>

            {editable ? (
              <>
                <p className="mt-3 text-[13px] font-medium text-neutral-tertiary">
                  아직 경매에 사용하지 않아 정보를 고칠 수 있어요.
                </p>
                <Link
                  to="/seller/products/$productId/edit"
                  params={{ productId: String(product.id) }}
                  className="ease-soft mt-4 flex h-11 w-fit items-center rounded-xl border border-brand-300 bg-card px-5 text-[14px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95"
                >
                  상품 수정
                </Link>
              </>
            ) : (
              <p className="mt-3 text-[13px] leading-[1.7] font-medium text-neutral-tertiary">
                상품은 한 번의 경매에만 쓰여요. 경매에 올라간 뒤로는 정보를 고칠
                수 없습니다.
              </p>
            )}
          </section>
        </div>
      </div>
    </AppShell>
  )
}
