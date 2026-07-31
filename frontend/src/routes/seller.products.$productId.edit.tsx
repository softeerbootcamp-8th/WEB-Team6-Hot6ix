import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_PRODUCTS } from '@/mocks/data'
import { ProductForm } from '@/features/seller/components/product-form'
import { StatusBadge } from '@/components/status-badge'
import { requireMember } from '@/lib/route-guards'

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
      <AppShell title="상품 수정" back className="max-w-[608px]">
        <EmptyState
          title="상품을 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 상품입니다."
        />
      </AppShell>
    )
  }

  const locked = product.status !== 'DRAFT'

  return (
    <AppShell title="상품 수정" back className="max-w-[608px]">
      <PageHeader
        title="상품 수정"
        description="경매가 시작된 상품은 수정할 수 없어요."
      />

      {locked && (
        <div className="mt-6 flex items-center gap-3 rounded-2xl bg-notice-surface px-4 py-3">
          <StatusBadge tone="notice">
            {product.status === 'IN_AUCTION' ? '경매 중' : '판매 완료'}
          </StatusBadge>
          <p className="text-label font-bold text-notice">
            이미 경매에 올라간 상품이라 내용을 바꿀 수 없어요.
          </p>
        </div>
      )}

      <section className="mt-6 rounded-4xl border bg-card p-6 md:p-8">
        <ProductForm
          initial={{
            name: product.name,
            category: product.category,
            description: product.description,
          }}
          submitLabel="저장하기"
          deletable={!locked}
          onSubmit={() => void navigate({ to: '/seller/products' })}
          onDelete={() => void navigate({ to: '/seller/products' })}
        />
      </section>
    </AppShell>
  )
}
