import { useSyncExternalStore } from 'react'

/**
 * 개발용 네트워크 시뮬레이션 플래그.
 *
 * API 를 붙일 때 로딩·실패 화면은 실제로 느리거나 실패해 봐야 확인할 수 있다.
 * 백엔드를 멈추거나 코드를 고치지 않고도 그 상황을 만들 수 있게 둔다.
 *
 * - `delayMs`   : 모든 요청에 이만큼 지연을 준다 (로딩 UI 확인)
 * - `failRate`  : 요청이 이 확률(%)로 실패한다 (에러 UI·재시도 확인)
 *
 * 값은 `localStorage` 에 남아 새로고침해도 유지된다.
 * **프로덕션 빌드에서는 항상 꺼진 값이고, 적용 코드도 번들에 들어가지 않는다.**
 */
export interface DevFlags {
  delayMs: number
  failRate: number
}

const STORAGE_KEY = 'upbid:dev-flags'
const OFF: DevFlags = { delayMs: 0, failRate: 0 }

function read(): DevFlags {
  if (!import.meta.env.DEV) return OFF
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return OFF
    const parsed = JSON.parse(raw) as Partial<DevFlags>
    return {
      delayMs: Number(parsed.delayMs) || 0,
      failRate: Number(parsed.failRate) || 0,
    }
  } catch {
    return OFF
  }
}

let current = read()
const listeners = new Set<() => void>()

export const devFlagsStore = {
  get: (): DevFlags => current,

  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },

  set(next: Partial<DevFlags>) {
    if (!import.meta.env.DEV) return
    current = { ...current, ...next }
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current))
    } catch {
      // 저장에 실패해도 이번 세션에는 반영된다.
    }
    listeners.forEach((listener) => listener())
  },

  reset() {
    devFlagsStore.set(OFF)
  },
}

/** 컴포넌트에서 플래그를 구독한다. 프로덕션에서는 항상 꺼진 값. */
export function useDevFlags(): DevFlags {
  return useSyncExternalStore(
    devFlagsStore.subscribe,
    devFlagsStore.get,
    () => OFF,
  )
}
