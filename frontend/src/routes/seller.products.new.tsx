import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { PageHeader } from '@/components/page-header'
import { ProductForm } from '@/features/seller/components/product-form'
import { requireMember } from '@/lib/route-guards'

export const Route = createFileRoute('/seller/products/new')({
  beforeLoad: requireMember,
  component: SellerProductNewPage,
})

function SellerProductNewPage() {
  const navigate = useNavigate()

  return (
    <AppShell title="상품 등록" back className="max-w-[608px]">
      <PageHeader
        title="상품 등록"
        description="등록한 상품은 경매방 물품으로 편성할 수 있어요."
      />

      <section className="mt-6 rounded-4xl border bg-card p-6 md:p-8">
        <ProductForm
          submitLabel="상품 등록하기"
          onSubmit={() => void navigate({ to: '/seller/products' })}
        />
      </section>
    </AppShell>
  )
}
