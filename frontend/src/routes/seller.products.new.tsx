import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { ProductForm } from '@/features/seller/components/product-form'
import { requireMember } from '@/lib/route-guards'
import { toProductErrorMessage } from '@/features/seller/product-error'
import { toast } from '@/lib/toast'
import { getGetListQueryKey, useCreate1 } from '@/api/generated/상품/상품'

/** 상품 등록 (Figma `WEB-06 · 판매자 · 상품 등록`, 713:3862). */
export const Route = createFileRoute('/seller/products/new')({
  beforeLoad: requireMember,
  component: SellerProductNewPage,
})

function SellerProductNewPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const createProduct = useCreate1()

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
          submitting={createProduct.isPending}
          onSubmit={(body) => {
            createProduct.mutate(
              { data: body },
              {
                // 서버가 등록한 뒤에만 성공으로 알린다.
                onSuccess: () => {
                  void queryClient.invalidateQueries({
                    queryKey: getGetListQueryKey(),
                  })
                  toast.success('상품을 등록했어요')
                  void navigate({ to: '/seller' })
                },
                onError: (error) => {
                  const { title, description } = toProductErrorMessage(error)
                  toast.error(title, { description })
                },
              },
            )
          }}
        />
      </div>
    </AppShell>
  )
}
