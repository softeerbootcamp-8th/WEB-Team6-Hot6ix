import { useEffect, useState } from 'react'

/**
 * 값이 잠잠해진 뒤에야 따라오는 사본.
 *
 * 검색어처럼 타이핑 한 글자마다 요청을 보내면 안 되는 값에 쓴다.
 * 타이머는 값이 다시 바뀌거나 화면을 떠날 때 반드시 정리한다.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
