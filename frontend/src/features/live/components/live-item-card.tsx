import { Clock, FastForward, Play } from 'lucide-react'
import { useState } from 'react'

import { ProductThumbnail } from '@/components/product-thumbnail'
import { AuctionCloseFlashOverlay } from '@/features/live/components/auction-close-flash-overlay'
import {
  AuctionStartFlashOverlay,
  type AuctionStartFlashState,
} from '@/features/live/components/auction-start-flash'
import { SoftCloseFlashOverlay } from '@/features/live/components/soft-close-flash-overlay'
import type { SoftCloseFlash } from '@/features/live/soft-close-flash'

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
  justExtended = null,
  justStarted = null,
  starting = false,
  closingEarly = false,
  softCloseTriggerSeconds = 0,
  rowRef,
  onSelect,
  onStart,
  onCloseEarly,
}: {
  item: AuctionItemDetail
  selected?: boolean
  /** 방을 만든 사람만 경매를 시작하거나 마감을 앞당길 수 있다. 구매자에게는 조작 줄을 숨긴다. */
  canStart?: boolean
  /** 지금 고를 수 없는 카드(빼기 선택 중의 진행·종료 물품) */
  dimmed?: boolean
  /** 방금 마감된 물품. 잠깐 "경매 종료" 도장이 찍힌다. */
  justClosed?: boolean
  /** 방금 소프트클로즈로 연장된 물품. `null` 이면 연출하지 않는다. */
  justExtended?: SoftCloseFlash | null
  /** 방금 경매가 시작된 물품. `null` 이면 연출하지 않는다. */
  justStarted?: AuctionStartFlashState | null
  /** 이 물품의 시작 요청을 서버가 아직 처리 중이다. */
  starting?: boolean
  /** 이 물품의 마감 앞당기기 요청을 서버가 아직 처리 중이다. */
  closingEarly?: boolean
  /**
   * 마감 몇 초 전부터 연장 구간인지. 앞당기기가 이 시각을 목적지로 삼으므로,
   * 이미 그 안에 들어온 물품은 앞당길 자리가 없어 버튼을 잠근다(서버도 거절한다).
   */
  softCloseTriggerSeconds?: number
  /** 목록이 자리를 옮길 때 쓰는 FLIP 참조 */
  rowRef?: (element: HTMLLIElement | null) => void
  onSelect?: () => void
  /** 진행 시간(분)을 정해 경매를 시작한다. 실제 호출은 라우트가 한다. */
  onStart?: (minutes: number) => void
  /** 마감을 연장 구간이 열리는 시각으로 앞당긴다. 확인 다이얼로그는 라우트가 띄운다. */
  onCloseEarly?: () => void
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
        className={cn(
          'flex w-full gap-3 p-3 text-left',
          // 아래에 조작 줄이 붙는 카드만 간격을 줄인다.
          (ready || (active && canStart && onCloseEarly)) && 'pb-1.5',
        )}
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
          <span className="mt-2">
            <span className="block text-[11px] font-medium text-neutral-tertiary">
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
                'block text-[16px] tabular-nums',
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
      {justClosed && <AuctionCloseFlashOverlay item={item} />}

      {/*
       * 소프트클로즈로 마감이 밀린 직후. 마감 도장과 같은 자리에 뜨지만
       * 경매가 계속되는 중이라 덮개를 밝게 깔아 아래 정보가 계속 읽히게 한다.
       */}
      {justExtended && !justClosed && (
        <SoftCloseFlashOverlay flash={justExtended} />
      )}

      {/* 방금 시작된 물품. 마감·연장과 같은 자리에 같은 규칙으로 뜬다. */}
      {justStarted && !justClosed && !justExtended && (
        <AuctionStartFlashOverlay flash={justStarted} />
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

      {/*
        진행 중 물품은 같은 자리에서 마감을 앞당길 수 있다. 시작 버튼과 자리를
        나눠 쓰는 이유는 한 카드에 조작이 하나뿐이기 때문이다 (시작 전이면 시작,
        진행 중이면 앞당기기).
      */}
      {active && canStart && onCloseEarly && (
        <div className="flex justify-end px-3 pb-2.5">
          <CloseEarlyControl
            itemName={item.name}
            pending={closingEarly}
            /*
             * 이미 연장 구간 안이면 앞당길 자리가 없다. 서버도 같은 이유로 거절한다.
             * 남은 시간이 0 인 물품도 잠근다 — 마감 이벤트를 기다리는 중이라
             * 화면에만 진행 중으로 남아 있는 상태다.
             */
            disabled={remaining <= softCloseTriggerSeconds}
            onCloseEarly={onCloseEarly}
          />
        </div>
      )}
    </li>
  )
}

/**
 * 진행 중 물품의 마감 앞당기기 버튼.
 *
 * 카드(`<button>`) 밖에 두는 이유는 `StartControl` 과 같다. 버튼 안에
 * 버튼을 넣을 수 없다.
 *
 * **눈에 띄지 않게 두는 것이 이 버튼의 요건이다.** 처음에는 시작 버튼처럼
 * 카드 폭을 채우는 빨간 버튼이었는데, 카드에서 제일 먼저 눈에 들어와서
 * 누르라고 재촉하는 것처럼 보였다. 자주 쓸 조작이 아니라 "필요하면 이런
 * 것도 된다" 정도로 알리면 되므로, 폭을 내용만큼만 잡고 색도 뺐다.
 * 강조는 진행 중 카드에서 이미 남은 시간과 현재가가 가져가고 있다.
 *
 * 누르면 바로 보내지 않고 라우트가 확인 다이얼로그를 띄운다. 되돌릴 수
 * 없는 조작이라 손이 스친 것만으로 마감이 당겨지면 안 된다.
 */
function CloseEarlyControl({
  itemName,
  pending,
  disabled,
  onCloseEarly,
}: {
  itemName: string
  pending: boolean
  disabled: boolean
  onCloseEarly: () => void
}) {
  return (
    <button
      type="button"
      aria-label={`${itemName} 경매 마감 앞당기기`}
      disabled={pending || disabled}
      onClick={onCloseEarly}
      className="ease-soft flex h-8 items-center gap-1 rounded-lg px-2 text-[12px] font-semibold text-neutral-tertiary transition-all duration-150 hover:bg-fill hover:text-neutral-secondary active:scale-95 disabled:cursor-not-allowed disabled:bg-transparent disabled:text-neutral-muted disabled:active:scale-100"
    >
      {pending ? (
        '앞당기는 중…'
      ) : (
        <>
          <FastForward aria-hidden className="size-3 fill-current" />
          {disabled ? '곧 마감돼요' : '마감 앞당기기'}
        </>
      )}
    </button>
  )
}

/** 진행 시간 기본값과 범위(분). 서버 `AuctionItemStartRequestDto` 와 같은 범위다. */
const DEFAULT_MINUTES = 30
const MIN_MINUTES = 1
const MAX_MINUTES = 720

/** 허용 범위 안으로 가둔다. */
function clamp(totalMinutes: number) {
  return Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, totalMinutes))
}

/** 총 분을 시·분 두 칸으로 쪼갠다. */
function toParts(totalMinutes: number) {
  return {
    hours: String(Math.floor(totalMinutes / 60)),
    minutes: String(totalMinutes % 60),
  }
}

/**
 * 시작 전 물품의 진행 시간 입력과 시작 버튼. 데스크톱과 모바일이 같은 카드를 쓴다.
 *
 * 단위를 입력 칸 밖에 두는 것은 값에 단위를 섞으면 커서가 단위 뒤로 가거나
 * 지우기 애매해서 어디까지가 입력값인지 헷갈리기 때문이다.
 *
 * 카드(`<button>`) 밖에 두는 이유: 버튼 안에 버튼을 넣을 수 없다.
 */
function StartControl({
  itemName,
  pending,
  onStart,
}: {
  itemName: string
  pending: boolean
  onStart: (minutes: number) => void
}) {
  /*
   * 두 칸 모두 입력 중에는 지우는 순간(빈 문자열)도 허용해야 해서 문자열로 들고 있는다.
   * 총 분은 이 둘에서 계산하므로 따로 상태를 두지 않는다 — 셋을 다 들고 있으면
   * 세 값이 어긋나는 경우를 매번 맞춰줘야 한다.
   */
  const [hoursDraft, setHoursDraft] = useState(toParts(DEFAULT_MINUTES).hours)
  const [minutesDraft, setMinutesDraft] = useState(
    toParts(DEFAULT_MINUTES).minutes,
  )

  const total = Number(hoursDraft || 0) * 60 + Number(minutesDraft || 0)

  /*
   * blur 하는 순간 clamp() 가 값을 조용히 되돌린다. 왜 바뀌었는지 알 수 없어서
   * 되돌리기 전에, 즉 입력하는 동안 범위를 알린다.
   */
  const outOfRange = total < MIN_MINUTES || total > MAX_MINUTES

  /** 비우거나 범위를 벗어난 값을 되돌리고, 90분처럼 넘치는 분도 시간으로 올린다. */
  const normalize = () => {
    const parts = toParts(clamp(total))
    setHoursDraft(parts.hours)
    setMinutesDraft(parts.minutes)
  }

  /** 숫자만 남기고 자릿수를 제한한다. 시간은 12까지, 분은 두 자리면 충분하다. */
  const digitsOnly = (value: string) => value.replace(/\D/g, '').slice(0, 2)

  const inputClassName =
    'w-6 bg-transparent text-right text-[13px] font-bold tabular-nums text-foreground outline-none disabled:opacity-50'
  const unitClassName = 'text-[11px] font-medium text-neutral-tertiary'

  return (
    <div className="relative flex items-center gap-2">
      {/*
        예전에는 5분 단위 −/+ 스테퍼였다. 최대가 12시간이 되면서 30분에서
        11시간으로 옮기는 데만 130번을 눌러야 해서, 버튼을 없애고 시·분을
        직접 적게 했다.
      */}
      <div
        className={cn(
          'flex h-[34px] flex-1 items-center justify-center gap-1 rounded-[10px] border bg-card px-2',
          outOfRange ? 'border-live' : 'border-border-strong',
        )}
      >
        <input
          inputMode="numeric"
          aria-label={`${itemName} 진행 시간(시간)`}
          aria-invalid={outOfRange}
          disabled={pending}
          value={hoursDraft}
          onChange={(event) => setHoursDraft(digitsOnly(event.target.value))}
          onBlur={normalize}
          className={inputClassName}
        />
        <span className={unitClassName}>시간</span>

        <input
          inputMode="numeric"
          aria-label={`${itemName} 진행 시간(분)`}
          aria-invalid={outOfRange}
          disabled={pending}
          value={minutesDraft}
          onChange={(event) => setMinutesDraft(digitsOnly(event.target.value))}
          onBlur={normalize}
          className={inputClassName}
        />
        <span className={unitClassName}>분</span>
      </div>

      <button
        type="button"
        aria-label={`${itemName} 경매 ${total}분 진행으로 시작`}
        disabled={pending || outOfRange}
        onClick={() => onStart(total)}
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
          1분~12시간 사이로 입력해요
        </p>
      )}
    </div>
  )
}
