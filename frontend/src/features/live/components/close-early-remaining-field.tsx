import { useState } from 'react'

import { formatClosingLead } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * 마감을 앞당길 때 얼마나 남길지 정하는 분·초 두 칸 입력.
 *
 * 시작 전 물품의 진행 시간(`StartControl`)과 같은 방식이다. 단위를 입력 칸 밖에
 * 두는 것도 같은 이유로, 값에 단위를 섞으면 커서가 단위 뒤로 가거나 지우기 애매해서
 * 어디까지가 입력값인지 헷갈린다.
 *
 * **범위가 물품마다 다르다.** 하한은 경매방의 연장 트리거이고(그보다 짧게 남기면
 * 앞당기는 즉시 연장 구간이라 알릴 순간이 없다), 상한은 지금 마감까지 남은 시간이다
 * (그보다 길면 앞당기기가 마감을 미루는 셈이 된다). 서버도 같은 두 조건으로 거절한다.
 */
export function CloseEarlyRemainingField({
  itemName,
  minSeconds,
  maxSeconds,
  disabled,
  onChange,
}: {
  itemName: string
  /** 남길 수 있는 최소 초. 경매방의 연장 트리거다. */
  minSeconds: number
  /** 남길 수 있는 최대 초. 지금 마감까지 남은 시간보다 짧아야 해서 1초를 뺀 값이다. */
  maxSeconds: number
  disabled: boolean
  /** 총 초와 범위를 벗어났는지. 벗어났으면 부모가 확인 버튼을 잠근다. */
  onChange: (totalSeconds: number, outOfRange: boolean) => void
}) {
  /*
   * 두 칸 모두 지우는 순간(빈 문자열)을 허용해야 해서 문자열로 들고 있는다.
   * 총 초는 이 둘에서 계산하므로 따로 상태를 두지 않는다 — 셋을 다 들고 있으면
   * 세 값이 어긋나는 경우를 매번 맞춰줘야 한다.
   */
  const [minutesDraft, setMinutesDraft] = useState(toParts(minSeconds).minutes)
  const [secondsDraft, setSecondsDraft] = useState(toParts(minSeconds).seconds)

  const total = Number(minutesDraft || 0) * 60 + Number(secondsDraft || 0)
  const outOfRange = total < minSeconds || total > maxSeconds

  /*
   * blur 하는 순간 clamp 가 값을 조용히 되돌린다. 왜 바뀌었는지 알 수 없어서
   * 되돌리기 전에, 즉 입력하는 동안 범위를 알린다.
   */
  const apply = (minutes: string, seconds: string) => {
    setMinutesDraft(minutes)
    setSecondsDraft(seconds)

    const next = Number(minutes || 0) * 60 + Number(seconds || 0)
    onChange(next, next < minSeconds || next > maxSeconds)
  }

  /** 비우거나 범위를 벗어난 값을 되돌리고, 90초처럼 넘치는 초도 분으로 올린다. */
  const normalize = () => {
    const clamped = Math.min(maxSeconds, Math.max(minSeconds, total))
    const parts = toParts(clamped)
    apply(parts.minutes, parts.seconds)
  }

  const inputClassName =
    'w-8 bg-transparent text-right text-[15px] font-bold tabular-nums text-foreground outline-none disabled:opacity-50'
  const unitClassName = 'text-[12px] font-medium text-neutral-tertiary'

  return (
    <div>
      <div
        className={cn(
          'flex h-11 items-center justify-center gap-1 rounded-xl border bg-card px-3',
          outOfRange ? 'border-live' : 'border-border-strong',
        )}
      >
        <input
          inputMode="numeric"
          aria-label={`${itemName} 마감까지 남길 시간(분)`}
          aria-invalid={outOfRange}
          disabled={disabled}
          value={minutesDraft}
          onChange={(event) =>
            apply(digitsOnly(event.target.value, 3), secondsDraft)
          }
          onBlur={normalize}
          className={inputClassName}
        />
        <span className={unitClassName}>분</span>

        <input
          inputMode="numeric"
          aria-label={`${itemName} 마감까지 남길 시간(초)`}
          aria-invalid={outOfRange}
          disabled={disabled}
          value={secondsDraft}
          onChange={(event) =>
            apply(minutesDraft, digitsOnly(event.target.value, 2))
          }
          onBlur={normalize}
          className={inputClassName}
        />
        <span className={unitClassName}>초</span>
      </div>

      {outOfRange && (
        <p role="alert" className="mt-1.5 text-[12px] font-medium text-live">
          {formatRange(minSeconds, maxSeconds)} 사이로 입력해요
        </p>
      )}
    </div>
  )
}

/** 총 초를 분·초 두 칸으로 쪼갠다. */
function toParts(totalSeconds: number) {
  return {
    minutes: String(Math.floor(totalSeconds / 60)),
    seconds: String(totalSeconds % 60),
  }
}

/** 숫자만 남기고 자릿수를 제한한다. */
function digitsOnly(value: string, length: number) {
  return value.replace(/\D/g, '').slice(0, length)
}

/**
 * 범위 안내 문구.
 *
 * 마감 문구와 같은 규칙(`formatClosingLead`)을 쓴다. 여기서 `5분 30초`로 고른 값이
 * 이벤트 피드에는 다른 형식으로 나오면 같은 값인지 알아보기 어렵다.
 */
function formatRange(minSeconds: number, maxSeconds: number) {
  return `${formatClosingLead(minSeconds)}~${formatClosingLead(maxSeconds)}`
}
