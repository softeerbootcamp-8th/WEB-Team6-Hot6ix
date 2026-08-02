import { useEffect, useRef } from 'react'

/**
 * 이벤트 줄이 "처음부터 있던 것"인지 "방금 들어온 것"인지 한 번만 정한다.
 *
 * 판정을 매번 다시 하면, 카운트다운 때문에 1초 뒤 리렌더가 일어날 때
 * 클래스가 바뀌며 애니메이션이 처음부터 다시 돈다. 그래서 방금 들어온 줄이
 * 순간 사라졌다 나타나 보였다.
 *
 * @returns id 로 `'initial' | 'incoming'` 을 돌려주는 함수
 */
export function useEventEntrance() {
  const mounted = useRef(false)
  const decided = useRef(new Map<number, 'initial' | 'incoming'>())

  useEffect(() => {
    mounted.current = true
  }, [])

  return (id: number) => {
    if (!decided.current.has(id)) {
      decided.current.set(id, mounted.current ? 'incoming' : 'initial')
    }
    return decided.current.get(id)!
  }
}
