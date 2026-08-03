import { Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { Search } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

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
  initialItems = [],
  onConfirm,
}: {
  open: boolean
  onClose: () => void
  /**
   * 이미 골라둔 물품. 목록에서 감추지 않고 **체크된 채로** 보여준다.
   * 감추면 한 번 고른 물품의 시작가를 고치거나 빼낼 방법이 없어진다.
   */
  initialItems?: PickedItem[]
  /** 고른 물품 **전체**를 돌려준다. 호출부는 기존 목록을 이걸로 교체한다. */
  onConfirm: (items: PickedItem[]) => void
}) {
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebouncedValue(keyword.trim())

  // 시작가는 입력 중 빈 문자열일 수 있어 문자열로 들고 있다가 확정할 때 숫자로 바꾼다.
  const [selected, setSelected] = useState<
    { productId: number; name: string; startingPrice: string }[]
  >([])

  // 열 때마다 바깥 목록으로 초기화한다. 열려 있는 동안 바깥이 바뀌어도
  // 입력 중인 값을 덮지 않도록 ref 로 최신값만 들고 있다가 열릴 때 한 번 쓴다.
  const initialItemsRef = useRef(initialItems)
  initialItemsRef.current = initialItems

  useEffect(() => {
    if (!open) return

    setKeyword('')
    setSelected(
      initialItemsRef.current.map((item) => ({
        productId: item.productId,
        name: item.name,
        startingPrice: String(item.startingPrice),
      })),
    )
  }, [open])

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
    () => products.filter((product) => product.productId != null),
    [products],
  )

  // 되돌리기는 여는 쪽 effect 가 맡는다. 여기서 비우면 닫히는 순간 목록이
  // 사라지는 게 보인다.
  const close = () => onClose()

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

  /*
   * 기본값을 넣지 않는다 — 그대로 두고 넘어간 값이 시작가가 되는 실수를 막는다.
   * 개수는 보지 않는다. 미리 고른 걸 전부 체크 해제해 비우는 것도 정상 동작이라
   * 0개일 때 버튼을 잠그면 되돌릴 방법이 없어진다.
   */
  const canConfirm = selected.every((item) => Number(item.startingPrice) > 0)

  return (
    <Modal
      open={open}
      onClose={close}
      labelledBy="pick-item-title"
      className="max-w-[760px] p-5 md:p-7"
    >
      <div className="flex max-h-[calc(100svh-7rem)] flex-col">
        <h2
          id="pick-item-title"
          className="shrink-0 text-[20px] font-extrabold text-foreground md:text-[24px]"
        >
          물품 추가
        </h2>
        <p className="mt-2 shrink-0 text-[14px] font-medium text-neutral-tertiary">
          고른 물품마다 시작가를 적고, 마지막에 한 번만 확인하면 돼요.
        </p>

        <div className="mt-5 flex min-h-0 flex-1 flex-col">
          <div className="relative shrink-0">
            <Search
              aria-hidden
              className="absolute top-1/2 left-4 size-4 -translate-y-1/2 text-neutral-muted"
            />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="상품명으로 검색"
              aria-label="상품명으로 검색"
              className="ease-soft h-12 w-full rounded-[14px] border bg-surface-subtle pr-4 pl-11 text-[14px] font-medium transition-colors duration-150 outline-none placeholder:text-neutral-muted focus-visible:border-brand-400"
            />
          </div>

          <p className="mt-4 shrink-0 text-[13px] font-bold text-neutral-secondary">
            {/* 커서 페이지네이션이라 전체 개수를 모른다. 더 있으면 "+"로 적는다. */}
            등록 상품 {visible.length}
            {hasNextPage ? '+' : ''}개
          </p>

          {isPending ? (
            <ul aria-hidden className="mt-3 space-y-3">
              {Array.from({ length: 4 }).map((_, index) => (
                <li
                  key={index}
                  className="animate-skeleton h-[84px] rounded-[18px] bg-fill"
                />
              ))}
            </ul>
          ) : isError ? (
            <p className="mt-3 rounded-[18px] bg-surface-subtle px-4 py-10 text-center text-[13px] font-medium text-neutral-tertiary">
              상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          ) : visible.length === 0 ? (
            <p className="mt-3 rounded-[18px] bg-surface-subtle px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
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
            <ul className="mt-3 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
              {visible.map((product) => {
                const productId = product.productId as number
                const name = product.name ?? ''
                const picked = selected.find(
                  (item) => item.productId === productId,
                )
                const checked = picked !== undefined

                return (
                  <li
                    key={productId}
                    className={cn(
                      'ease-soft flex flex-wrap items-center gap-x-4 gap-y-3 rounded-[18px] border p-3 transition-colors duration-150',
                      checked
                        ? 'border-brand-300 bg-brand-50/40'
                        : 'bg-card hover:border-border-strong',
                    )}
                  >
                    {/* 행 전체가 체크박스다. 시작가 칸은 button 안에 둘 수 없어 형제로 뺀다. */}
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      onClick={() => toggle(productId, name)}
                      className="flex min-w-[220px] flex-1 items-center gap-3 text-left"
                    >
                      <span
                        aria-hidden
                        className={cn(
                          'flex size-7 shrink-0 items-center justify-center rounded-lg border text-[14px] font-extrabold text-white',
                          checked ? 'border-brand-500 bg-brand-500' : 'bg-card',
                        )}
                      >
                        {checked && '✓'}
                      </span>

                      <ProductThumbnail
                        name={name}
                        src={product.imageUrl}
                        size={200}
                        iconClassName="size-5"
                        className="flex size-14 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-500"
                      />

                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[15px] font-bold text-foreground">
                          {name}
                        </span>
                        <span className="mt-1 block truncate text-[12px] font-medium text-neutral-tertiary">
                          {product.createdAt
                            ? `${formatDate(product.createdAt)} 등록`
                            : '-'}
                        </span>
                      </span>
                    </button>

                    {/* 고른 물품에만 나타난다. 좁은 화면에서는 아래 줄로 흐른다. */}
                    {checked && (
                      <div className="flex shrink-0 items-center gap-2">
                        <label
                          htmlFor={`picker-start-price-${productId}`}
                          className="text-[12px] font-semibold text-neutral-tertiary"
                        >
                          시작가
                        </label>
                        <input
                          id={`picker-start-price-${productId}`}
                          inputMode="numeric"
                          value={
                            picked.startingPrice
                              ? Number(picked.startingPrice).toLocaleString(
                                  'ko-KR',
                                )
                              : ''
                          }
                          placeholder="0"
                          onChange={(event) =>
                            setStartingPrice(productId, event.target.value)
                          }
                          className="h-11 w-[120px] rounded-xl border bg-card px-3 text-right text-[14px] font-semibold outline-none focus-visible:border-brand-400"
                        />
                        <span className="text-[13px] font-semibold text-neutral-tertiary">
                          원
                        </span>
                      </div>
                    )}
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
        </div>

        {/* 목록이 길어져도 늘 보이도록 아래에 고정한다. */}
        <div className="mt-5 flex shrink-0 items-center gap-3 border-t pt-5">
          <p className="text-[13px] font-bold text-neutral-secondary">
            {selected.length}개 선택
            {selected.length > 0 && !canConfirm && (
              <span className="ml-2 font-semibold text-live">
                시작가를 모두 입력해 주세요
              </span>
            )}
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
            className="ease-soft ml-auto flex h-12 min-w-[140px] items-center justify-center rounded-[14px] bg-brand-500 px-6 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-40 disabled:active:scale-100"
          >
            선택 완료
          </button>
        </div>
      </div>
    </Modal>
  )
}
