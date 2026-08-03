import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { PRODUCT_STATUS, canEditProduct } from '@/features/seller/product-status'
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

  const { data, isPending, isError, error, refetch } = useGetDetail(
    Number(productId),
  )
  const updateProduct = useUpdate1()

  if (isPending) return <RoutePending />

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
                  // PUT 은 전체 교체다. 조회로 받은 이미지를 그대로 되돌려 보내지
                  // 않으면 저장할 때마다 서버의 이미지가 지워진다.
                  data: { ...body, imageUrl: product.imageUrl },
                },
                {
                  // 서버가 저장한 뒤에만 성공으로 알린다.
                  onSuccess: () => {
                    void queryClient.invalidateQueries({
                      queryKey: getGetListQueryKey(),
                    })
                    void queryClient.invalidateQueries({
                      queryKey: getGetDetailQueryKey(Number(productId)),
                    })
                    toast.success('상품 정보를 수정했어요')
                    void navigate({ to: '/seller/products' })
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
