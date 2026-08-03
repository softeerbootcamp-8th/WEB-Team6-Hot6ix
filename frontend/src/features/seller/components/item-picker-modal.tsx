import { Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { Search } from 'lucide-react'
import { useMemo, useState } from 'react'

import { Modal } from '@/components/ui/modal'
import { cn } from '@/lib/utils'
import { formatDate } from '@/lib/format'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useProductList } from '@/features/seller/use-product-list'

/**
 * 경매방에 넣을 물품 하나. 시작가는 서버에서 필수라 여기서 반드시 받아야 한다.
 * `name` 은 요청에 안 들어가지만, 고른 물품을 화면에 보여주려면 필요하다 —
 * 호출부가 상품 목록을 따로 들고 있지 않기 때문이다.
 */
export interface PickedItem {
  productId: number
  name: string
  startingPrice: number
}

/**
 * 물품 추가 (Figma `WEB-09 · 판매자 · 경매방 생성 / 물품 추가 참고`, 713:3965).
 *
 * Figma 는 전체 화면으로 그려두었지만 경매방 생성 흐름에서 벗어나지 않도록
 * 모달로 얹었다. 안쪽 구성은 프레임 그대로다 — 820 목록 패널 + 24 간격 +
 * 372 선택 패널, 높이 720.
 *
 * **시작가를 이 모달에서 받는다.** 원래 Figma 안내는 "경매방 생성 화면에서 설정"
 * 이었는데, 같은 모달을 쓰는 라이브 화면에는 시작가 입력 자리가 아예 없어서
 * 방송 중 물품 추가가 값을 정할 수 없었다. 서버는 `startingPrice` 가 필수라
 * 두 화면 모두 여기서 받는 쪽으로 합쳤다.
 *
 * 목록은 이 모달이 직접 조회한다. 검색이 서버 파라미터라 호출부가 상품 배열을
 * 넘기는 구조로는 첫 페이지 안에서만 걸러지기 때문이다.
 */
export function ItemPickerModal({
  open,
  onClose,
  excludeProductIds = [],
  onConfirm,
}: {
  open: boolean
  onClose: () => void
  /** 이미 이 경매방에 넣어둔 상품. 목록에서 감춘다. */
  excludeProductIds?: number[]
  onConfirm: (items: PickedItem[]) => void
}) {
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebouncedValue(keyword.trim())

  // 시작가는 입력 중 빈 문자열일 수 있어 문자열로 들고 있다가 확정할 때 숫자로 바꾼다.
  const [selected, setSelected] = useState<
    { productId: number; name: string; startingPrice: string }[]
  >([])

  const {
    products,
    isPending,
    isError,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useProductList({
    // 이미 경매에 올라간 상품은 서버가 거절하므로 처음부터 안 보여준다.
    status: 'UNREGISTERED',
    keyword: debouncedKeyword || undefined,
  })

  const visible = useMemo(
    () =>
      products.filter(
        (product) =>
          product.productId != null &&
          !excludeProductIds.includes(product.productId),
      ),
    [products, excludeProductIds],
  )

  const close = () => {
    setKeyword('')
    setSelected([])
    onClose()
  }

  const toggle = (productId: number, name: string) =>
    setSelected((prev) =>
      prev.some((item) => item.productId === productId)
        ? prev.filter((item) => item.productId !== productId)
        : [...prev, { productId, name, startingPrice: '' }],
    )

  const setStartingPrice = (productId: number, raw: string) =>
    setSelected((prev) =>
      prev.map((item) =>
        item.productId === productId
          ? { ...item, startingPrice: raw.replace(/\D/g, '') }
          : item,
      ),
    )

  // 기본값을 넣지 않는다 — 그대로 두고 넘어간 값이 시작가가 되는 실수를 막는다.
  const canConfirm =
    selected.length > 0 &&
    selected.every((item) => Number(item.startingPrice) > 0)

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
        여러 개를 한 번에 고르고 시작가까지 정한 뒤 마지막에 한 번만 확인하면
        돼요.
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
            {/* 커서 페이지네이션이라 전체 개수를 모른다. 더 있으면 "+"로 적는다. */}
            등록 상품 {visible.length}
            {hasNextPage ? '+' : ''}개
          </p>

          {isPending ? (
            <ul aria-hidden className="mt-2.5 space-y-5 pt-2.5">
              {Array.from({ length: 4 }).map((_, index) => (
                <li
                  key={index}
                  className="animate-skeleton h-[106px] rounded-[20px] bg-fill"
                />
              ))}
            </ul>
          ) : isError ? (
            <p className="mt-4 rounded-[20px] bg-surface-subtle px-4 py-10 text-center text-[13px] font-medium text-neutral-tertiary">
              상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          ) : visible.length === 0 ? (
            <p className="mt-4 rounded-[20px] bg-surface-subtle px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
              {debouncedKeyword ? (
                '검색 결과가 없어요.'
              ) : (
                <>
                  추가할 수 있는 상품이 없어요.{' '}
                  <Link
                    to="/seller/products/new"
                    className="font-bold text-brand-500 hover:underline"
                  >
                    상품 등록하기
                  </Link>
                </>
              )}
            </p>
          ) : (
            <ul className="mt-2.5 min-h-0 flex-1 space-y-5 overflow-y-auto pt-2.5 pr-1">
              {visible.map((product) => {
                const productId = product.productId as number
                const name = product.name ?? ''
                const checked = selected.some(
                  (item) => item.productId === productId,
                )

                return (
                  <li key={productId}>
                    <button
                      type="button"
                      aria-pressed={checked}
                      onClick={() => toggle(productId, name)}
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
                        name={name}
                        src={product.imageUrl}
                        size={200}
                        className="flex size-[72px] shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                      />

                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[16px] font-bold text-foreground">
                          {name}
                        </span>
                        <span className="mt-2 block truncate text-[13px] font-medium text-neutral-tertiary">
                          {product.createdAt
                            ? `${formatDate(product.createdAt)} 등록`
                            : '-'}
                        </span>
                      </span>
                    </button>
                  </li>
                )
              })}

              {hasNextPage && (
                <li>
                  <button
                    type="button"
                    disabled={isFetchingNextPage}
                    onClick={() => void fetchNextPage()}
                    className="ease-soft h-12 w-full rounded-[14px] border bg-card text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.99] disabled:opacity-50"
                  >
                    {isFetchingNextPage ? '불러오는 중…' : '더 보기'}
                  </button>
                </li>
              )}
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

          {selected.length === 0 ? (
            <p className="mt-8 text-[13px] font-medium text-neutral-muted">
              왼쪽에서 물품을 골라주세요.
            </p>
          ) : (
            <ul className="mt-8 min-h-0 flex-1 space-y-6 overflow-y-auto pr-1">
              {selected.map((item) => (
                <li key={item.productId}>
                  <p className="truncate text-[15px] font-bold text-foreground">
                    {item.name}
                  </p>

                  <label
                    htmlFor={`picker-start-price-${item.productId}`}
                    className="mt-2.5 block text-[11px] font-semibold text-neutral-tertiary"
                  >
                    시작가
                  </label>
                  <div className="mt-1 flex items-center gap-2">
                    <input
                      id={`picker-start-price-${item.productId}`}
                      inputMode="numeric"
                      value={
                        item.startingPrice
                          ? Number(item.startingPrice).toLocaleString('ko-KR')
                          : ''
                      }
                      placeholder="0"
                      onChange={(event) =>
                        setStartingPrice(item.productId, event.target.value)
                      }
                      className="h-11 min-w-0 flex-1 rounded-xl border bg-card px-3 text-right text-[14px] font-semibold outline-none focus-visible:border-brand-400"
                    />
                    <span className="shrink-0 text-[13px] font-semibold text-neutral-tertiary">
                      원
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}

          <p className="mt-6 shrink-0 rounded-[14px] bg-brand-50 px-5 py-5.5 text-[13px] leading-[1.6] font-semibold whitespace-pre-line text-brand-500">
            {'물품마다 시작가를 입력해야\n경매방에 추가할 수 있어요.'}
          </p>

          <button
            type="button"
            disabled={!canConfirm}
            onClick={() => {
              onConfirm(
                selected.map((item) => ({
                  productId: item.productId,
                  name: item.name,
                  startingPrice: Number(item.startingPrice),
                })),
              )
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
