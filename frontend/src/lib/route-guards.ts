import { redirect } from '@tanstack/react-router'

import { sessionStore, type SessionUser } from '@/lib/session'

/**
 * 로그인이 필요한 라우트의 `beforeLoad` 로 쓴다.
 *
 * 화면 진입을 막을 뿐이고 **권한 검증이 아니다.** 실제 권한은 백엔드가
 * 확인하므로, 여기를 통과했다고 데이터 접근이 보장되지는 않는다.
 */
export function requireMember({ location }: { location: { href: string } }): {
  user: SessionUser
} {
  const session = sessionStore.getState()

  if (session.status !== 'member') {
    throw redirect({ to: '/', search: { redirect: location.href } })
  }

  return { user: session.user }
}
