import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { ProductForm } from '@/features/seller/components/product-form'
import { requireMember } from '@/lib/route-guards'

/** 상품 등록 (Figma `WEB-06 · 판매자 · 상품 등록`, 713:3862). */
export const Route = createFileRoute('/seller/products/new')({
  beforeLoad: requireMember,
  component: SellerProductNewPage,
})

function SellerProductNewPage() {
  const navigate = useNavigate()

  return (
    <AppShell title="상품 등록" back className="max-w-[1280px]">
      <div className="hidden md:block">
        <h1 className="text-[28px] font-extrabold text-foreground">
          상품 등록
        </h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          경매에 사용할 상품 정보를 등록하세요.
        </p>
      </div>

      <div className="mt-4">
        <ProductForm
          submitLabel="상품 등록"
          uploadText="이미지 업로드"
          // TODO: POST /api/v1/products 연동 (현재 목업)
          onSubmit={() => void navigate({ to: '/seller/products' })}
        />
      </div>
    </AppShell>
  )
}
