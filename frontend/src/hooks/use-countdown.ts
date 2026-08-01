import { useEffect, useState } from 'react'

/**
 * 마감까지 남은 초를 1초마다 갱신한다.
 *
 * **이 값으로 경매 종료를 확정하지 않는다.** 서버가 마감을 판정하고, 여기는
 * 표시용일 뿐이다. 0 이 되어도 서버 이벤트가 오기 전까지는 마감으로 다루지
 * 않는다.
 */
export function useCountdown(endsAt: string): number {
  const target = new Date(endsAt).getTime()

  const [remaining, setRemaining] = useState(() =>
    Math.max(0, Math.floor((target - Date.now()) / 1000)),
  )

  useEffect(() => {
    setRemaining(Math.max(0, Math.floor((target - Date.now()) / 1000)))

    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.floor((target - Date.now()) / 1000))
      setRemaining(next)
      if (next === 0) window.clearInterval(timer)
    }, 1000)

    return () => window.clearInterval(timer)
  }, [target])

  return remaining
}

/** 마감 임박(60초 이하) 여부. 카운트다운 색을 바꾸는 기준이다. */
export function isClosingSoon(remainingSeconds: number): boolean {
  return remainingSeconds > 0 && remainingSeconds <= 60
}
