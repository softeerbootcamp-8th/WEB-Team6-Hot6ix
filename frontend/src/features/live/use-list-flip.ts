import { useLayoutEffect, useRef } from 'react'

/**
 * 목록 안에서 항목이 자리를 옮기면 그 이동을 눈에 보이게 한다 (FLIP).
 *
 * 마감된 물품이 "진행 중" 묶음에서 "경매 종료" 묶음으로 내려갈 때처럼,
 * 순서가 바뀌는 순간 DOM 은 그냥 튄다. 그리기 직전 위치를 기억했다가
 * 이전 자리에서 새 자리로 되감아 재생하면 아래로 내려가는 게 보인다.
 *
 * 반환된 `Map` 에 `ref` 콜백으로 항목을 등록한다. 키는 화면이 다시 그려져도
 * 같은 항목을 가리키는 값(보통 id)이어야 한다.
 *
 * `prefers-reduced-motion` 이면 움직이지 않는다. Web Animations API 라
 * 애니메이션이 스스로 끝나므로 정리할 것이 없다.
 */
export function useListFlip<T extends HTMLElement>(
  /** 순서가 바뀌었는지 판단할 값. 보통 `id:status` 를 이어 붙인다. */
  signature: string,
  { duration = 460 }: { duration?: number } = {},
) {
  const nodes = useRef(new Map<number, T>())
  const positions = useRef(new Map<number, number>())
  const mounted = useRef(false)

  useLayoutEffect(() => {
    const reduced = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    const first = !mounted.current
    mounted.current = true

    nodes.current.forEach((element, key) => {
      /*
       * **뷰포트가 아니라 `offsetTop` 으로 잰다.** `getBoundingClientRect().top`
       * 은 스크롤 위치가 섞인 좌표라, 목록이 스크롤되거나 위쪽 레이아웃이 밀리면
       * 이전 값과 기준이 달라진다. 그러면 한 칸 움직인 항목이 수백 px 을 이동한
       * 것으로 계산돼 다른 영역 위까지 솟아오른다.
       */
      const next = element.offsetTop
      const previous = positions.current.get(key)
      positions.current.set(key, next)

      if (reduced || first || previous === undefined) return
      const delta = previous - next
      if (delta === 0) return

      // 먼저 돌던 것이 남아 있으면 어긋난 자리에 멈춘다.
      element.getAnimations().forEach((animation) => animation.cancel())

      element.animate(
        [
          { transform: `translateY(${delta}px)` },
          // 내려가는 항목은 목표 지점에서 살짝 눌렸다 자리를 잡는다.
          { transform: `translateY(${delta < 0 ? 4 : -4}px)`, offset: 0.75 },
          { transform: 'none' },
        ],
        { duration, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
      )
    })

    positions.current.forEach((_, key) => {
      if (!nodes.current.has(key)) positions.current.delete(key)
    })
  }, [signature, duration])

  return nodes
}
