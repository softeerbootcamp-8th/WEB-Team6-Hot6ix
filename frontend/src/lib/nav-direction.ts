import type { AnyRouter } from '@tanstack/react-router'

/**
 * 뒤로 가는 중인지를 `<html data-nav="back">` 으로 알린다.
 *
 * View Transitions API 에는 방향 개념이 없어서, 앞으로 갈 때와 뒤로 갈 때에
 * 같은 연출이 걸린다. 앞으로 갈 때는 옛 화면이 버텨 주는 게 맞지만(빈 순간이
 * 안 생긴다), 뒤로 갈 때는 방금 떠난 화면이 그만큼 새 화면 위로 비쳐서 한 번
 * 깜빡인 것처럼 보인다. 실제 연출 차이는 `globals.css` 에 있다.
 *
 * 방향은 라우터 히스토리가 알려준다(`PUSH` · `REPLACE` · `FORWARD` · `BACK`).
 * `popstate` 를 직접 듣지 않는 이유는 그것만으로는 뒤로와 앞으로를 못 가르기
 * 때문이다.
 *
 * 표시는 다음 이동 때까지 남는다. 이 속성은 전환이 도는 동안에만 읽히므로
 * 그사이에 다른 영향을 주지 않는다.
 */
/**
 * 히스토리가 알려주는 이동 종류. `@tanstack/history` 의 `HistoryAction` 에서
 * 구독자에게 오지 않는 `GO` 를 뺀 것과 같다.
 *
 * 그 패키지를 직접 import 하지 않는 이유는 라우터가 딸려 들고 오는 것이라
 * `package.json` 에 없기 때문이다. 여기서 필요한 건 `type` 하나뿐이다.
 */
type NavAction = { type: 'PUSH' | 'REPLACE' | 'FORWARD' | 'BACK' }

export function trackNavDirection(router: AnyRouter): () => void {
  const root = document.documentElement

  return router.history.subscribe(({ action }: { action: NavAction }) => {
    if (action.type === 'BACK') root.dataset.nav = 'back'
    else delete root.dataset.nav
  })
}
