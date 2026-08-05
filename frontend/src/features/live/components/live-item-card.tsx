import { Clock, Minus, Play, Plus } from 'lucide-react'
import { useState } from 'react'

import { ProductThumbnail } from '@/components/product-thumbnail'

import { formatRemaining, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail } from '@/mocks/types'

/** Figma `c*_status` 표기 그대로 쓴다. */
const STATUS_LABEL = {
  READY: '시작 전',
  ACTIVE: '진행 중',
  CLOSED: '경매 종료',
} as const

/** 상태별 점·글자색. 셋이 서로 다른 색이어야 한눈에 갈린다. */
const STATUS_TONE = {
  READY: { dot: 'bg-notice', text: 'text-notice' },
  ACTIVE: { dot: 'bg-live', text: 'text-live' },
  CLOSED: { dot: 'bg-neutral-muted', text: 'text-neutral-tertiary' },
} as const

/**
 * 라이브 왼쪽 열의 물품 카드 (Figma `card*_bg`).
 *
 * 카드 폭 316, 썸네일 72, 상태 점 7px. 진행 중이면 상태와 남은 시간이
 * 빨강, 아니면 회색이다. 마감 임박은 굵기까지 달라진다.
 */
export function LiveItemCard({
  item,
  selected = false,
  canStart = false,
  dimmed = false,
  justClosed = false,
  starting = false,
  rowRef,
  onSelect,
  onStart,
}: {
  item: AuctionItemDetail
  selected?: boolean
  /** 방을 만든 사람만 경매를 시작할 수 있다. 구매자에게는 조작 줄을 숨긴다. */
  canStart?: boolean
  /** 지금 고를 수 없는 카드(빼기 선택 중의 진행·종료 물품) */
  dimmed?: boolean
  /** 방금 마감된 물품. 잠깐 "경매 종료" 도장이 찍힌다. */
  justClosed?: boolean
  /** 이 물품의 시작 요청을 서버가 아직 처리 중이다. */
  starting?: boolean
  /** 목록이 자리를 옮길 때 쓰는 FLIP 참조 */
  rowRef?: (element: HTMLLIElement | null) => void
  onSelect?: () => void
  /** 진행 시간(분)을 정해 경매를 시작한다. 실제 호출은 라우트가 한다. */
  onStart?: (minutes: number) => void
}) {
  const remaining = useCountdown(item.endsAt)
  const active = item.status === 'ACTIVE'
  const ready = item.status === 'READY'
  const closed = item.status === 'CLOSED'
  const urgent = active && isClosingSoon(remaining)
  const tone = STATUS_TONE[item.status]

  return (
    /*
     * 카드 테두리는 `li` 가 그린다. 시작 전 물품의 시간 조절·시작 버튼이
     * 같은 카드 안에 들어가야 하는데, 버튼 안에 버튼을 넣을 수 없어서
     * 선택 영역(button)과 조작 영역을 형제로 두고 테두리만 공유한다.
     */
    <li
      ref={rowRef}
      className={cn(
        'ease-soft relative overflow-hidden rounded-2xl border bg-card transition-all duration-200',
        selected
          ? 'border-brand-400 ring-1 ring-brand-200'
          : 'hover:border-border-strong',
        dimmed && 'opacity-50',
      )}
    >
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={selected}
        className={cn('flex w-full gap-3 p-3 text-left', ready && 'pb-1.5')}
      >
        <ProductThumbnail
          src={item.imageUrl}
          className="flex size-[72px] shrink-0 items-center justify-center rounded-xl bg-fill text-neutral-muted"
        />

        <span className="flex min-w-0 flex-1 flex-col">
          <span className="flex items-center gap-1.5">
            {/* 시작 전과 종료가 둘 다 회색이면 구분이 안 된다. 셋을 다르게 쓴다. */}
            <span
              aria-hidden
              className={cn('size-[7px] shrink-0 rounded-full', tone.dot)}
            />
            <span className={cn('text-[12px] font-semibold', tone.text)}>
              {STATUS_LABEL[item.status]}
            </span>

            {!closed && !ready && (
              <span
                className={cn(
                  'ml-auto flex items-center gap-1 text-[11px] tabular-nums',
                  urgent
                    ? 'font-bold text-live'
                    : 'font-medium text-neutral-tertiary',
                )}
              >
                <Clock aria-hidden className="size-[13px]" />
                {formatRemaining(remaining)}
              </span>
            )}
          </span>

          <span className="mt-1.5 block truncate text-[15px] font-bold text-foreground">
            {item.name}
          </span>

          {/* 시작 전에는 시작가를 보여준다. 구매자도 얼마부터인지 알아야 한다. */}
          <span className="mt-2 flex items-baseline justify-between">
            <span className="text-[11px] font-medium text-neutral-tertiary">
              {closed
                ? item.sold
                  ? '낙찰가'
                  : '유찰'
                : ready
                  ? '시작가'
                  : '현재가'}
            </span>
            <span
              className={cn(
                'text-[18px] tabular-nums',
                ready
                  ? 'font-bold text-neutral-secondary'
                  : cn(
                      'text-brand-500',
                      closed ? 'font-bold' : 'font-extrabold',
                    ),
              )}
            >
              {formatWon(ready ? item.startPrice : item.currentPrice)}
            </span>
          </span>
        </span>
      </button>

      {/* 마감된 직후에만 도장이 덮였다가 사라진다. */}
      {justClosed && (
        <span
          aria-hidden
          className="animate-closed-stamp absolute inset-0 z-10 flex items-center justify-center rounded-2xl"
        >
          <span className="animate-closed-label rounded-xl border-2 border-white bg-live px-4 py-1.5 text-[15px] font-extrabold tracking-wide text-white shadow-lg">
            경매 종료
          </span>
        </span>
      )}

      {/* 시작 전 물품은 방 주인이 진행 시간을 정해 바로 시작할 수 있다. */}
      {ready && canStart && onStart && (
        <div className="px-3 pb-3">
          <StartControl
            itemName={item.name}
            pending={starting}
            onStart={onStart}
          />
        </div>
      )}
    </li>
  )
}

/** 진행 시간 기본값과 조절 폭(분). 초 단위까지 고를 일은 없다. */
const DEFAULT_MINUTES = 30
const MINUTE_STEP = 5
const MIN_MINUTES = 5
const MAX_MINUTES = 180

/**
 * 시작 전 물품의 진행 시간 조절과 시작 버튼.
 *
 * 예전에는 `30분 00초` 를 통째로 적는 자유 입력이었다. 단위가 글자 안에
 * 섞여 있어서 지우고 다시 쓰기 번거롭고, 잘못 적으면 알 방법도 없었다.
 * 5분 단위 스테퍼로 바꾸고 단위는 입력 칸 밖에 뒀다.
 *
 * 카드(`<button>`) 밖에 두는 이유: 버튼 안에 버튼을 넣을 수 없다.
 */
/** 허용 범위 안으로 가둔다. */
function clamp(value: number) {
  return Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, value))
}

function StartControl({
  itemName,
  pending,
  onStart,
}: {
  itemName: string
  pending: boolean
  onStart: (minutes: number) => void
}) {
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES)
  /** 입력 중에는 지우는 순간(빈 문자열)도 허용해야 해서 따로 들고 있는다. */
  const [draft, setDraft] = useState(String(DEFAULT_MINUTES))

  const shift = (delta: number) => {
    const next = clamp(minutes + delta)
    setMinutes(next)
    setDraft(String(next))
  }

  /*
   * blur 하는 순간 clamp() 가 값을 조용히 되돌린다. 왜 바뀌었는지 알 수 없어서
   * 되돌리기 전에, 즉 입력하는 동안 범위를 알린다.
   */
  const outOfRange =
    draft !== '' && (Number(draft) < MIN_MINUTES || Number(draft) > MAX_MINUTES)

  return (
    <div className="relative flex items-center gap-2">
      <div
        className={cn(
          'flex h-[34px] flex-1 items-center rounded-[10px] border bg-card',
          outOfRange ? 'border-live' : 'border-border-strong',
        )}
      >
        <button
          type="button"
          onClick={() => shift(-MINUTE_STEP)}
          disabled={pending || minutes <= MIN_MINUTES}
          aria-label={`${itemName} 진행 시간 ${MINUTE_STEP}분 줄이기`}
          className="ease-soft flex h-full w-8 shrink-0 items-center justify-center rounded-l-[10px] text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-40"
        >
          <Minus aria-hidden className="size-3.5" />
        </button>

        {/* 스테퍼로도, 직접 입력으로도 바꿀 수 있다. */}
        <span className="flex min-w-0 flex-1 items-baseline justify-center gap-0.5">
          <input
            inputMode="numeric"
            aria-label={`${itemName} 진행 시간(분)`}
            aria-invalid={outOfRange}
            disabled={pending}
            value={draft}
            onChange={(event) => {
              const next = event.target.value.replace(/\D/g, '').slice(0, 3)
              setDraft(next)
              const parsed = Number(next)
              if (next !== '' && parsed >= MIN_MINUTES)
                setMinutes(clamp(parsed))
            }}
            onBlur={() => {
              // 비우거나 범위를 벗어난 값은 되돌려 놓는다.
              const parsed = Number(draft)
              const next = draft === '' ? minutes : clamp(parsed)
              setMinutes(next)
              setDraft(String(next))
            }}
            className="w-8 bg-transparent text-right text-[13px] font-bold tabular-nums text-foreground outline-none"
          />
          <span className="text-[11px] font-medium text-neutral-tertiary">
            분
          </span>
        </span>

        <button
          type="button"
          onClick={() => shift(MINUTE_STEP)}
          disabled={pending || minutes >= MAX_MINUTES}
          aria-label={`${itemName} 진행 시간 ${MINUTE_STEP}분 늘리기`}
          className="ease-soft flex h-full w-8 shrink-0 items-center justify-center rounded-r-[10px] text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-40"
        >
          <Plus aria-hidden className="size-3.5" />
        </button>
      </div>

      <button
        type="button"
        aria-label={`${itemName} 경매 ${minutes}분 진행으로 시작`}
        disabled={pending}
        onClick={() => onStart(minutes)}
        className="ease-soft flex h-[34px] w-[86px] shrink-0 items-center justify-center gap-1.5 rounded-[10px] bg-success text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
      >
        {pending ? (
          '시작 중…'
        ) : (
          <>
            <Play aria-hidden className="size-2.5 fill-current" />
            시작
          </>
        )}
      </button>

      {/*
        카드 높이가 늘면 목록 전체가 밀려서, 자리를 차지하지 않게 겹쳐 그린다.
        아래가 아니라 위로 띄우는 이유: 카드가 `overflow-hidden` 이라
        (둥근 모서리와 마감 도장이 이걸 쓴다) 아래로 나가면 잘려서 안 보인다.
      */}
      {outOfRange && (
        <p
          role="alert"
          className="pointer-events-none absolute bottom-full left-0 z-10 mb-1 rounded-md bg-card px-1.5 py-0.5 text-[11px] font-medium text-live shadow-sm"
        >
          {MIN_MINUTES}~{MAX_MINUTES}분 사이로 입력해요
        </p>
      )}
    </div>
  )
}
