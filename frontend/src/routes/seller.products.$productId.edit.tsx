import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import {
  PRODUCT_STATUS,
  canEditProduct,
} from '@/features/seller/product-status'
import { ProductForm } from '@/features/seller/components/product-form'
import { RouteError, RoutePending } from '@/components/route-states'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'
import { toProductErrorMessage } from '@/features/seller/product-error'
import { toast } from '@/lib/toast'
import {
  getGetDetailQueryKey,
  getGetListQueryKey,
  useGetDetail,
  useUpdate1,
} from '@/api/generated/상품/상품'

/** 상품 수정 (Figma `WEB-07 · 판매자 · 상품 수정`, 713:3883). */
export const Route = createFileRoute('/seller/products/$productId/edit')({
  beforeLoad: requireMember,
  component: SellerProductEditPage,
})

function SellerProductEditPage() {
  const { productId } = Route.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data, isPending, isStale, isFetching, isError, error, refetch } =
    useGetDetail(Number(productId))
  const updateProduct = useUpdate1()

  /*
   * 폼은 **갓 받아온 값으로만 만든다.**
   *
   * `ProductForm` 은 `initial` 을 첫 렌더에서 한 번만 읽어 폼 상태로 옮긴다.
   * 캐시에 남아 있던 예전 정보로 그려지면, 뒤늦게 도착한 새 값은 화면에
   * 반영되지 않는다 — 판매자가 방금 저장한 내용이 아니라 수정 전 정보를 보게 된다.
   * 그래서 낡은 값을 다시 받아오는 중에는 폼을 그리지 않고 기다린다.
   *
   * 창 복귀 refetch 는 꺼져 있어서(`lib/query-client.ts`) 입력 중에 이 조건이
   * 켜지지는 않는다. 마운트 직후의 재조회만 여기 걸린다.
   */
  if (isPending || (isStale && isFetching)) return <RoutePending />

  // 404 는 "없는 상품"이라 장애 화면 대신 안내로 보낸다.
  if (error?.response?.status === 404) {
    return (
      <AppShell title="상품 수정" back className="max-w-[1280px]">
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
      <AppShell title="상품 수정" back className="max-w-[1280px]">
        <EmptyState
          title="상품을 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 상품입니다."
        />
      </AppShell>
    )
  }
  // 경매가 시작된 뒤에는 서버가 409 로 막는다. 화면도 같은 기준으로 잠근다.
  const locked = !canEditProduct(product.status)
  const status = PRODUCT_STATUS[product.status ?? 'UNREGISTERED']

  return (
    <AppShell title="상품 수정" back className="max-w-[1280px]">
      <div className="hidden md:block">
        <h1 className="text-[28px] font-extrabold text-foreground">
          상품 수정
        </h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          등록된 상품 정보를 수정하세요.
        </p>
      </div>

      {locked ? (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-[20px] border bg-card px-6 py-5">
          <span
            className={cn(
              'flex h-8 w-28 shrink-0 items-center justify-center rounded-2xl text-[12px] font-bold',
              status.className,
            )}
          >
            {status.label}
          </span>
          <p className="text-[14px] font-medium text-neutral-tertiary">
            이미 경매에 올라간 상품이라 내용을 바꿀 수 없어요.
          </p>
        </div>
      ) : (
        <div className="mt-4">
          <ProductForm
            initial={product}
            submitLabel="수정 완료"
            uploadText="상품 이미지 변경"
            submitting={updateProduct.isPending}
            onSubmit={(body) => {
              updateProduct.mutate(
                {
                  productId: Number(productId),
                  /*
                   * 폼이 만든 값을 그대로 보낸다. 여기서 `imageUrl` 을 조회 값으로 덮으면
                   * 방금 올린 주소가 지워져서, 저장은 성공하는데 사진만 안 바뀐다(#213).
                   *
                   * PUT 전체 교체라 값을 빼면 서버가 지우는 건 맞다. 그건 폼이 막는다 —
                   * 파일을 새로 안 고르면 조회로 받은 주소를 그대로 되돌려 넣는다
                   * (`product-form.tsx:168`).
                   */
                  data: body,
                },
                {
                  // 서버가 저장한 뒤에만 성공으로 알린다.
                  onSuccess: () => {
                    void queryClient.invalidateQueries({
                      queryKey: getGetListQueryKey(),
                    })
                    /*
                     * 상세는 **stale 표시만** 하고 여기서 다시 받아오지 않는다.
                     *
                     * 이 화면이 상세 쿼리를 보고 있어서 그냥 무효화하면 곧바로
                     * refetch 가 시작되는데, 응답이 오기 전에 아래 `navigate` 로
                     * 화면이 사라진다. 그러면 TanStack Query 가 그 요청을
                     * `revert: true` 로 취소해 **캐시를 수정 전 값으로 되돌린다**
                     * (Orval queryFn 이 `signal` 을 쓰므로 취소 대상이 된다).
                     * 다음에 이 화면으로 들어올 때 받아오면 충분하다.
                     */
                    void queryClient.invalidateQueries({
                      queryKey: getGetDetailQueryKey(Number(productId)),
                      refetchType: 'none',
                    })
                    toast.success('상품 정보를 수정했어요')
                    void navigate({ to: '/seller' })
                  },
                  onError: (updateError) => {
                    const { title, description } =
                      toProductErrorMessage(updateError)
                    toast.error(title, { description })
                  },
                },
              )
            }}
          />
        </div>
      )}
    </AppShell>
  )
}
