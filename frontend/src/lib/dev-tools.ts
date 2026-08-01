import { useSyncExternalStore } from 'react'

/**
 * 개발용 보조 UI(세션 전환기·라우터 devtools·경매방 DEV 바)를 한 번에 켜고 끈다.
 *
 * 화면을 그대로 보고 싶을 때가 있어서 스위치를 뒀다. 세 가지로 정해진다.
 *
 * 1. `localStorage` 에 저장된 값 (⌘/Ctrl + Shift + D 로 토글)
 * 2. 없으면 `.env` 의 `VITE_DEV_TOOLS` (`off` 면 꺼진 채로 시작)
 * 3. 프로덕션 빌드에서는 무조건 꺼진다
 */
const STORAGE_KEY = 'upbid:dev-tools'

function initial() {
  if (!import.meta.env.DEV) return false
  const saved = window.localStorage.getItem(STORAGE_KEY)
  if (saved !== null) return saved === 'on'
  return import.meta.env.VITE_DEV_TOOLS !== 'off'
}

let enabled = import.meta.env.DEV ? initial() : false
const listeners = new Set<() => void>()

export const devToolsStore = {
  get: () => enabled,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  toggle() {
    if (!import.meta.env.DEV) return
    enabled = !enabled
    window.localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off')
    listeners.forEach((listener) => listener())
  },
}

/** 개발용 UI 를 지금 그려도 되는지. 프로덕션에서는 언제나 false. */
export function useDevTools(): boolean {
  return useSyncExternalStore(
    devToolsStore.subscribe,
    devToolsStore.get,
    () => false,
  )
}
