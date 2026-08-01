import { Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { Search } from 'lucide-react'
import { useMemo, useState } from 'react'

import { Modal } from '@/components/ui/modal'
import { cn } from '@/lib/utils'
import type { Product } from '@/mocks/types'

/**
 * 물품 추가 (Figma `WEB-09 · 판매자 · 경매방 생성 / 물품 추가 참고`, 713:3965).
 *
 * Figma 는 전체 화면으로 그려두었지만 경매방 생성 흐름에서 벗어나지 않도록
 * 모달로 얹었다. 안쪽 구성은 프레임 그대로다 — 820 목록 패널 + 24 간격 +
 * 372 선택 패널, 높이 720. 목록 행은 106 높이에 체크박스 32 / 썸네일 72다.
 *
 * 행 두 번째 줄은 Figma 가 "시작가 N원"인데, 시작가는 상품이 아니라 경매
 * 물품에 붙는 값이라 상품에는 없다. 그래서 분류를 대신 보여준다.
 * 시작가는 Figma 안내대로 경매방 생성 화면에서 정한다.
 */
export function ItemPickerModal({
  open,
  onClose,
  products,
  onConfirm,
}: {
  open: boolean
  onClose: () => void
  /** 아직 이 경매방에 넣지 않은 상품만 넘긴다. */
  products: Product[]
  onConfirm: (productIds: number[]) => void
}) {
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<number[]>([])

  const visible = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return products
    return products.filter((product) => product.name.includes(trimmed))
  }, [products, keyword])

  const selectedProducts = products.filter((product) =>
    selected.includes(product.id),
  )

  const close = () => {
    setKeyword('')
    setSelected([])
    onClose()
  }

  const toggle = (id: number) =>
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((value) => value !== id) : [...prev, id],
    )

  return (
    <Modal
      open={open}
      onClose={close}
      labelledBy="pick-item-title"
      className="max-w-[1216px] p-5 md:p-7"
    >
      <h2
        id="pick-item-title"
        className="text-[20px] font-extrabold text-foreground md:text-[28px]"
      >
        물품 추가
      </h2>
      <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
        등록한 상품 중 이번 경매에서 판매할 물품을 선택하세요.
      </p>

      <div className="mt-4 grid gap-6 lg:grid-cols-[minmax(0,1fr)_372px]">
        {/* 목록 패널 — 820×720 */}
        <section className="flex flex-col rounded-[20px] border bg-card p-5 lg:h-[min(720px,calc(100svh-19rem))] lg:p-7">
          <div className="relative shrink-0">
            <Search
              aria-hidden
              className="absolute top-1/2 left-5 size-4 -translate-y-1/2 text-neutral-muted"
            />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="상품명으로 검색"
              aria-label="상품명으로 검색"
              className="ease-soft h-[52px] w-full rounded-[14px] border bg-surface-subtle pr-5 pl-12 text-[14px] font-medium transition-colors duration-150 outline-none placeholder:text-neutral-muted focus-visible:border-brand-400"
            />
          </div>

          <p className="mt-7 shrink-0 text-[15px] font-extrabold text-foreground">
            등록 상품 {products.length}개
          </p>

          {visible.length === 0 ? (
            <p className="mt-4 rounded-[20px] bg-surface-subtle px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
              {products.length === 0 ? (
                <>
                  추가할 수 있는 상품이 없어요.{' '}
                  <Link
                    to="/seller/products/new"
                    className="font-bold text-brand-500 hover:underline"
                  >
                    상품 등록하기
                  </Link>
                </>
              ) : (
                '검색 결과가 없어요.'
              )}
            </p>
          ) : (
            <ul className="mt-2.5 min-h-0 flex-1 space-y-5 overflow-y-auto pt-2.5 pr-1">
              {visible.map((product) => {
                const checked = selected.includes(product.id)

                return (
                  <li key={product.id}>
                    <button
                      type="button"
                      aria-pressed={checked}
                      onClick={() => toggle(product.id)}
                      className={cn(
                        'ease-soft flex h-[106px] w-full items-center gap-5 rounded-[20px] border px-5 text-left transition-all duration-150 active:scale-[0.995]',
                        checked
                          ? 'border-brand-300 bg-[#f7fbff]'
                          : 'bg-card hover:border-border-strong',
                      )}
                    >
                      <span
                        aria-hidden
                        className={cn(
                          'flex size-8 shrink-0 items-center justify-center rounded-lg border text-[16px] font-extrabold text-white',
                          checked ? 'border-brand-500 bg-brand-500' : 'bg-card',
                        )}
                      >
                        {checked && '✓'}
                      </span>

                      <ProductThumbnail
                        name={product.name}
                        size={200}
                        className="flex size-[72px] shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                      />

                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[16px] font-bold text-foreground">
                          {product.name}
                        </span>
                        <span className="mt-2 block truncate text-[13px] font-medium text-neutral-tertiary">
                          {product.category}
                        </span>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        {/* 선택 패널 — 372×720 */}
        <section className="flex flex-col rounded-[20px] border bg-card p-5 lg:h-[min(720px,calc(100svh-19rem))] lg:p-7">
          <div className="flex shrink-0 items-center justify-between gap-3">
            <h3 className="text-[19px] font-extrabold text-foreground">
              선택한 물품
            </h3>
            <span className="flex h-[34px] min-w-[116px] items-center justify-center rounded-[17px] bg-brand-50 px-4 text-[13px] font-extrabold text-brand-500">
              {selected.length}개
            </span>
          </div>

          {selectedProducts.length === 0 ? (
            <p className="mt-8 text-[13px] font-medium text-neutral-muted">
              왼쪽에서 물품을 골라주세요.
            </p>
          ) : (
            <ul className="mt-8 min-h-0 flex-1 space-y-8 overflow-y-auto">
              {selectedProducts.map((product) => (
                <li key={product.id}>
                  <p className="truncate text-[15px] font-bold text-foreground">
                    {product.name}
                  </p>
                  <p className="mt-2.5 text-[13px] font-medium text-neutral-tertiary">
                    시작가 설정 필요
                  </p>
                </li>
              ))}
            </ul>
          )}

          <p className="mt-6 shrink-0 rounded-[14px] bg-brand-50 px-5 py-5.5 text-[13px] leading-[1.6] font-semibold whitespace-pre-line text-brand-500">
            {'선택한 물품은 경매방 생성 화면에서\n시작가를 설정할 수 있어요.'}
          </p>

          <button
            type="button"
            disabled={selected.length === 0}
            onClick={() => {
              onConfirm(selected)
              close()
            }}
            className="ease-soft mt-auto flex h-14 shrink-0 items-center justify-center rounded-[14px] bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-40 disabled:active:scale-100"
          >
            선택 완료 ({selected.length})
          </button>
        </section>
      </div>
    </Modal>
  )
}
