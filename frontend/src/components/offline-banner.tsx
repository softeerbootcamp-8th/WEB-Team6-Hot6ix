import { WifiOff } from 'lucide-react'
import { useEffect, useState } from 'react'

/**
 * 전역 오프라인 배너.
 *
 * `__root.tsx` 에 한 번만 둔다. 라이브 화면의 `ConnectionBanner` 는
 * **실시간 채널** 상태(SSE/WebSocket 재연결)를 다루고, 이건 **기기 네트워크**
 * 자체가 끊긴 상태를 다룬다. 둘은 원인이 달라서 따로 둔다.
 *
 * 화면 맨 위에 얇게 깔고, 복구되면 스스로 사라진다.
 *
 * `fixed` 도 `sticky` 도 아닌 **일반 흐름**이다. 모바일 라이브·물품 상세·
 * 종료된 방의 앱바가 `sticky top-0` 이라, 배너까지 위에 붙으면 같은 자리에서
 * 겹쳐 뒤로가기 버튼을 가린다. 화면 맨 위에 한 번 깔고 스크롤과 함께 올라간다.
 */
export function OfflineBanner() {
  // SSR 이 없어 초기값을 바로 읽어도 되지만, navigator 가 없는 환경을 대비한다.
  const [online, setOnline] = useState(
    () => typeof navigator === 'undefined' || navigator.onLine,
  )

  useEffect(() => {
    const goOnline = () => setOnline(true)
    const goOffline = () => setOnline(false)

    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
    return () => {
      window.removeEventListener('online', goOnline)
      window.removeEventListener('offline', goOffline)
    }
  }, [])

  if (online) return null

  return (
    <div
      role="status"
      aria-live="polite"
      className="animate-rise relative z-[60] flex h-11 items-center justify-center gap-2 bg-neutral-strong px-5 text-white"
    >
      <WifiOff aria-hidden className="size-4 shrink-0" />
      <p className="truncate text-[13px] font-semibold">
        인터넷 연결이 끊겼어요. 연결되면 자동으로 이어집니다.
      </p>
    </div>
  )
}
