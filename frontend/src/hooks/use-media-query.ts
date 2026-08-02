import { useSyncExternalStore } from 'react'

/**
 * 미디어 쿼리 구독.
 *
 * **CSS 로 트리를 숨기는 것과 다르다.** `md:hidden` 은 `display:none` 일 뿐
 * 컴포넌트는 그대로 마운트된다. 그래서 모바일 트리 안의 `<dialog>` 가
 * `showModal()` 되면 **문서 전체가 inert 가 되어 데스크톱 화면의 버튼이
 * 전부 죽는다.** 타이머·구독도 두 벌 돈다.
 *
 * 두 트리 중 하나만 그려야 하는 화면에서는 CSS 대신 이걸 쓴다.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => window.matchMedia(query).matches,
    // SSR 이 없지만 첫 렌더 안전값으로 데스크톱을 가정한다.
    () => true,
  )
}

/**
 * Tailwind `lg` 기준. 이 값이 false 면 모바일 전용 트리를 그린다.
 *
 * 라이브 화면의 3열 레이아웃과 "페이지는 스크롤하지 않는다" 규칙이 모두
 * `lg` 에서 켜진다. 예전에는 `md` 를 썼는데, 768~1023 구간에서 데스크톱
 * 트리가 세로로 쌓이면서 페이지 전체가 스크롤됐다.
 */
export function useIsDesktop(): boolean {
  return useMediaQuery('(min-width: 1024px)')
}
