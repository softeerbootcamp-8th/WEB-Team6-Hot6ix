import { useEffect, useRef } from 'react'

import { toast } from '@/lib/toast'

/**
 * 전역 오프라인 알림.
 *
 * `__root.tsx` 에 한 번만 둔다. 기기 네트워크가 끊기면 토스트를 한 번만 띄운다.
 * 복구되면 또 한 번 알린다. 끊긴 동안 같은 메시지를 반복하지 않는다.
 */
export function OfflineBanner() {
  const wasOfflineRef = useRef(false)

  useEffect(() => {
    const goOffline = () => {
      if (wasOfflineRef.current) return
      wasOfflineRef.current = true
      toast.error('인터넷 연결이 끊겼어요. 연결되면 자동으로 이어집니다.')
    }
    const goOnline = () => {
      if (!wasOfflineRef.current) return
      wasOfflineRef.current = false
      toast.info('인터넷 연결이 복구됐어요.')
    }

    window.addEventListener('offline', goOffline)
    window.addEventListener('online', goOnline)
    return () => {
      window.removeEventListener('offline', goOffline)
      window.removeEventListener('online', goOnline)
    }
  }, [])

  return null
}
