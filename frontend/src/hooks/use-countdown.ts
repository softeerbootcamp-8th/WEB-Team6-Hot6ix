import { useSyncExternalStore } from 'react'

/**
 * 1초에 한 번 도는 공용 tick.
 *
 * 예전에는 `useCountdown` 이 컴포넌트마다 `setInterval` 을 하나씩 만들었다.
 * 물품이 20개면 목록 카드와 리더보드, 빠른 입찰 카드까지 타이머가 40개를
 * 넘었고, **마운트 시점이 제각각이라 같은 화면의 숫자들이 서로 다른 순간에
 * 바뀌었다.** 하나로 모으면 전부 같은 프레임에서 넘어간다.
 *
 * 구독자가 하나도 없으면 타이머를 멈춘다. 라이브 화면을 벗어난 뒤에도 계속
 * 도는 것을 막으려는 것이다.
 */
let now = Date.now()
let timerId: number | null = null
const listeners = new Set<() => void>()

function tick() {
  now = Date.now()
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)

  if (timerId === null) {
    now = Date.now()
    timerId = window.setInterval(tick, 1000)
    /*
     * 백그라운드 탭에서는 브라우저가 타이머를 1분에 한 번까지 늦춘다. 돌아온
     * 순간 다시 재지 않으면 멈춰 있던 숫자가 한참 뒤에야 따라잡는다.
     *
     * 여기서 맞추는 건 화면에 적히는 남은 시간뿐이다. 마감 판정과 금액은
     * 여전히 서버가 정한다.
     */
    document.addEventListener('visibilitychange', tick)
  }

  return () => {
    listeners.delete(listener)
    if (listeners.size === 0 && timerId !== null) {
      window.clearInterval(timerId)
      timerId = null
      document.removeEventListener('visibilitychange', tick)
    }
  }
}

function getNow() {
  return now
}

/** 셀 것이 없는 컴포넌트가 tick 을 구독하지 않게 하는 빈 구독. */
const NEVER_TICKS = () => () => {}

/**
 * 마감까지 남은 초를 1초마다 갱신한다.
 *
 * **이 값으로 경매 종료를 확정하지 않는다.** 서버가 마감을 판정하고, 여기는
 * 표시용일 뿐이다. 0 이 되어도 서버 이벤트가 오기 전까지는 마감으로 다루지
 * 않는다 (표기는 `formatCountdown` 참고).
 *
 * `endsAt` 이 `null` 이면 0 이다. 시작 전 물품이 그렇고, 이 경우 부르는 쪽이
 * 카운트다운 대신 "시작 전"을 그려야 한다.
 */
export function useCountdown(endsAt: string | null): number {
  const target = endsAt === null ? null : new Date(endsAt).getTime()
  const valid = target !== null && Number.isFinite(target)

  /*
   * 이미 0 이거나 셀 마감 시각이 없으면 tick 을 구독하지 않는다. 시작 전·마감된
   * 물품까지 매초 다시 그리면 목록이 길어질수록 그만큼 헛돈다.
   */
  const counting = valid && target > now
  const currentNow = useSyncExternalStore(
    counting ? subscribe : NEVER_TICKS,
    getNow,
    getNow,
  )

  if (!valid) return 0
  return Math.max(0, Math.floor((target - currentNow) / 1000))
}

/**
 * 마감 임박(60초 이하) 여부. 카운트다운 색을 바꾸는 기준이다.
 *
 * **0 도 임박으로 본다.** 남은 시간이 0 인데 서버 마감 이벤트가 아직 안 온
 * 물품은 "마감 처리 중"으로 적히는데, 그때 색까지 평상시로 돌아가면 방금까지
 * 빨갛던 줄이 갑자기 회색이 되어 이미 끝난 것처럼 보인다.
 *
 * **부르는 쪽이 진행 중인 물품인지 먼저 가른다.** 시작 전 물품은 마감 시각이
 * 없어 남은 시간이 늘 0 이라, 그냥 넘기면 전부 임박으로 표시된다.
 */
export function isClosingSoon(remainingSeconds: number): boolean {
  return remainingSeconds <= 60
}
