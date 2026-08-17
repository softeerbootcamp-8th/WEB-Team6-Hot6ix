import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState, PageHeader } from '@/components/page-header'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { RouteError, RoutePending } from '@/components/route-states'
import { toDeals } from '@/features/trades/adapt-deal'
import { useGetDeals } from '@/api/generated/거래-내역/거래-내역'
import {
  PRODUCT_STATUS,
  canDeleteProduct,
  canEditProduct,
} from '@/features/seller/product-status'
import { cn } from '@/lib/utils'
import { formatDate, formatWon } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import { toProductErrorMessage } from '@/features/seller/product-error'
import { toast } from '@/lib/toast'
import {
  getGetListQueryKey,
  useDelete1,
  useGetDetail,
} from '@/api/generated/상품/상품'

/**
 * 상품 상세.
 *
 * 목록에서 상품을 누르면 여기로 온다. 예전에는 경매를 거친 상품을 누르면
 * 거래 내역 목록으로 보냈는데, 어떤 상품을 눌러도 같은 곳으로 가서
 * "이 상품이 어떻게 됐는지"를 알 수 없었다.
 *
 * 상품 정보 + 경매 상태 + (결과가 있으면) 그 거래로 가는 길을 함께 둔다.
 */
export const Route = createFileRoute('/seller/products/$productId/')({
  beforeLoad: requireMember,
  component: SellerProductDetailPage,
})

function SellerProductDetailPage() {
  const { productId } = Route.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const { data, isPending, isError, error, refetch } = useGetDetail(
    Number(productId),
  )
  /*
   * 이 상품의 경매 결과. 거래 목록에서 `productId` 로 찾는다.
   *
   * 실패해도 화면을 막지 않는다. 거래는 아래 한 섹션일 뿐이라, 못 받으면 아직
   * 경매를 안 거친 상품과 같은 안내가 나간다. 상품 정보 자체는 위 조회가 원본이다.
   */
  const dealsQuery = useGetDeals()
  const deleteProduct = useDelete1()

  if (isPending) return <RoutePending />

  // 404 는 "없는 상품"이라 장애 화면 대신 안내로 보낸다.
  if (error?.response?.status === 404) {
    return (
      <AppShell title="상품 상세" back className="max-w-[1280px]">
        <EmptyState
          title="상품을 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 상품입니다."
        />
      </AppShell>
    )
  }

  if (isError && error)
    return <RouteError error={error} reset={() => void refetch()} />

  const product = data.data

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
  const status = PRODUCT_STATUS[product.status ?? 'UNREGISTERED']
  const editable = canEditProduct(product.status)
  const deletable = canDeleteProduct(product.status)
  /*
   * 경매를 거친 상품은 거래가 남는다. `productId` 로 정확히 잇는다.
   *
   * 재등록을 지원해서 한 상품이 여러 거래를 가질 수 있다. 서버가 최근 마감 순으로
   * 주므로 먼저 걸리는 것이 가장 최근 거래다.
   */
  const trade = toDeals(dealsQuery.data?.data).find(
    (candidate) => candidate.productId === product.productId,
  )

  return (
    <AppShell title="상품 상세" back className="max-w-[1280px]">
      <PageHeader
        title="상품 상세"
        description="등록한 상품 정보와 경매 상태를 확인하세요."
      />

      <div className="mt-5 grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
        {/* 왼쪽 · 사진과 기본 정보 */}
        <section className="rounded-[20px] border bg-card p-6">
          <div className="relative">
            <ProductThumbnail
              src={product.imageUrl}
              iconClassName="size-10"
              className="flex aspect-square w-full items-center justify-center rounded-2xl bg-fill text-neutral-muted"
            />

            <span
              className={cn(
                'absolute top-3 left-3 flex h-7 items-center rounded-full px-3 text-[12px] font-bold shadow-sm',
                status.className,
              )}
            >
              {status.label}
            </span>
          </div>

          {/* 띄어쓰기 없는 긴 이름이 그냥 두면 안 쪼개져서 카드를 밀어낸다. */}
          <h2 className="mt-5 text-[22px] leading-[1.35] font-extrabold break-words text-foreground">
            {product.name}
          </h2>

          <dl className="mt-4 divide-y rounded-2xl border bg-surface-subtle px-4">
            <div className="flex items-center justify-between gap-3 py-3">
              <dt className="text-[12px] font-medium text-neutral-tertiary">
                등록일
              </dt>
              <dd className="text-[13px] font-bold tabular-nums text-foreground">
                {product.createdAt ? formatDate(product.createdAt) : '-'}
              </dd>
            </div>
            {product.referenceUrl && (
              <div className="flex items-center justify-between gap-3 py-3">
                <dt className="text-[12px] font-medium text-neutral-tertiary">
                  참고 링크
                </dt>
                <dd className="min-w-0">
                  <a
                    href={product.referenceUrl}
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
                <dl className="mt-4 grid grid-cols-[minmax(0,1fr)] gap-3 sm:grid-cols-3">
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      경매방
                    </dt>
                    <dd className="mt-1 truncate text-[14px] font-bold text-foreground">
                      {trade.auctionRoomName}
                    </dd>
                  </div>
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      낙찰가
                    </dt>
                    <dd className="mt-1 text-[14px] font-extrabold tabular-nums text-brand-500">
                      {/* 유찰이면 낙찰가가 없다. */}
                      {trade.amount === null ? '유찰' : formatWon(trade.amount)}
                    </dd>
                  </div>
                  <div className="rounded-2xl bg-surface-subtle px-4 py-3.5">
                    <dt className="text-[12px] font-medium text-neutral-tertiary">
                      마감일
                    </dt>
                    <dd className="mt-1 text-[14px] font-bold tabular-nums text-foreground">
                      {trade.closedAt ? formatDate(trade.closedAt) : '-'}
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
              <p className="mt-3 text-[13px] leading-[1.7] font-medium text-neutral-tertiary">
                {product.status === 'IN_PROGRESS'
                  ? '지금 경매방에서 진행 중인 상품이에요. 결과가 확정되면 여기에 표시됩니다.'
                  : product.status === 'ENDED'
                    ? '경매를 마친 상품이에요. 거래 내역에서 진행 상황을 확인할 수 있어요.'
                    : product.status === 'READY'
                      ? '경매방에 물품으로 담겨 있어요. 경매가 시작되면 여기에 표시됩니다.'
                      : '아직 경매에 사용하지 않은 상품이에요. 경매방을 만들 때 물품으로 넣을 수 있어요.'}
              </p>
            )}
          </section>

          <section className="rounded-[20px] border bg-card p-6 md:p-7">
            <h2 className="text-[16px] font-bold text-foreground">상품 관리</h2>

            <p className="mt-3 text-[13px] leading-[1.7] font-medium text-neutral-tertiary">
              {editable
                ? deletable
                  ? '아직 경매에 사용하지 않아 정보를 고치거나 지울 수 있어요.'
                  : '경매방에 담겨 있어 정보는 고칠 수 있지만, 지우려면 경매방에서 먼저 빼야 해요.'
                : '상품은 한 번의 경매에만 쓰여요. 경매에 올라간 뒤로는 정보를 고칠 수 없습니다.'}
            </p>

            {(editable || deletable) && (
              <div className="mt-4 flex flex-wrap gap-2">
                {editable && (
                  <Link
                    to="/seller/products/$productId/edit"
                    params={{ productId: String(product.productId) }}
                    className="ease-soft flex h-11 items-center rounded-xl border border-brand-300 bg-card px-5 text-[14px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95"
                  >
                    상품 수정
                  </Link>
                )}
                {deletable && (
                  <Button
                    type="button"
                    variant="dangerOutline"
                    className="h-11 rounded-xl px-5 text-[14px] font-bold"
                    onClick={() => setConfirmingDelete(true)}
                  >
                    삭제
                  </Button>
                )}
              </div>
            )}
          </section>
        </div>
      </div>

      <ConfirmDialog
        open={confirmingDelete}
        title="이 상품을 삭제할까요?"
        description="삭제한 상품은 되돌릴 수 없어요."
        confirmLabel="삭제"
        tone="danger"
        pending={deleteProduct.isPending}
        onCancel={() => setConfirmingDelete(false)}
        onConfirm={() => {
          deleteProduct.mutate(
            { productId: Number(productId) },
            {
              // 서버가 지운 뒤에만 성공으로 알린다.
              onSuccess: () => {
                void queryClient.invalidateQueries({
                  queryKey: getGetListQueryKey(),
                })
                toast.success('상품을 삭제했어요')
                void navigate({ to: '/seller' })
              },
              onError: (deleteError) => {
                const { title, description } =
                  toProductErrorMessage(deleteError)
                setConfirmingDelete(false)
                toast.error(title, { description })
              },
            },
          )
        }}
      />
    </AppShell>
  )
}
