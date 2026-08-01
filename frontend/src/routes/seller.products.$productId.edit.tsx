import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { PRODUCT_RESULT } from '@/features/seller/product-status'
import { ProductForm } from '@/features/seller/components/product-form'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'

/** 상품 수정 (Figma `WEB-07 · 판매자 · 상품 수정`, 713:3883). */
export const Route = createFileRoute('/seller/products/$productId/edit')({
  beforeLoad: requireMember,
  component: SellerProductEditPage,
})

function SellerProductEditPage() {
  const { productId } = Route.useParams()
  const navigate = useNavigate()

  const product = MOCK_PRODUCTS.find(
    (candidate) => String(candidate.id) === productId,
  )

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

  // 상품은 한 번의 경매에만 쓰인다. 경매에 올라간 뒤에는 못 고친다.
  const locked = product.status !== 'DRAFT'
  const result = PRODUCT_RESULT[product.status]

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
              result.className,
            )}
          >
            {result.label}
          </span>
          <p className="text-[14px] font-medium text-neutral-tertiary">
            이미 경매에 올라간 상품이라 내용을 바꿀 수 없어요.
          </p>
        </div>
      ) : (
        <div className="mt-4">
          <ProductForm
            initial={{
              name: product.name,
              category: product.category,
              description: product.description,
              productUrl: product.productUrl ?? '',
            }}
            submitLabel="수정 완료"
            uploadText="상품 이미지 변경"
            // TODO: PUT /api/v1/products/{id} 연동 (현재 목업)
            onSubmit={() => void navigate({ to: '/seller/products' })}
          />
        </div>
      )}
    </AppShell>
  )
}
