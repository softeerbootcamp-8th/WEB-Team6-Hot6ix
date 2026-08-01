import { useSyncExternalStore } from 'react'

/**
 * 앱 전역 알림 스토어.
 *
 * 화면 어디서든 `toast.success('저장했어요')` 로 띄운다. 실제 렌더는
 * `components/ui/toaster.tsx` 가 `__root.tsx` 에서 한 번만 한다.
 *
 * React 밖(라우트 `beforeLoad`, API 인터셉터)에서도 띄울 수 있어야 해서
 * 세션 스토어와 같은 모듈 레벨 스토어로 둔다.
 *
 * **서버 응답을 받기 전에 성공 토스트를 띄우지 않는다.** 입찰·낙찰·마감은
 * 서버가 확정해 준 뒤에만 성공으로 알린다 (루트 CLAUDE.md).
 */

export type ToastTone = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  tone: ToastTone
  message: string
  /** 아래 줄에 붙는 보조 설명. 물품명·금액처럼 맥락을 준다. */
  description?: string
  /** 자동으로 닫히기까지의 시간(ms). 0 이면 사용자가 닫을 때까지 남는다. */
  duration: number
}

type Options = Omit<Partial<Toast>, 'id' | 'message'>

let nextId = 1
let toasts: Toast[] = []
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

function push(tone: ToastTone, message: string, options: Options = {}): number {
  const id = nextId++
  // 호출부가 duration·description 만 덮어쓸 수 있게 options 를 뒤에 편다.
  toasts = [...toasts, { duration: 4000, ...options, id, tone, message }]
  emit()
  return id
}

export const toast = {
  success: (message: string, options?: Options) =>
    push('success', message, options),
  error: (message: string, options?: Options) =>
    push('error', message, options),
  info: (message: string, options?: Options) => push('info', message, options),

  dismiss(id: number) {
    toasts = toasts.filter((item) => item.id !== id)
    emit()
  },

  clear() {
    toasts = []
    emit()
  },
}

const EMPTY: Toast[] = []

/** `Toaster` 만 쓴다. 화면 코드에서는 `toast.*` 를 부르면 된다. */
export function useToasts(): Toast[] {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => toasts,
    () => EMPTY,
  )
}
