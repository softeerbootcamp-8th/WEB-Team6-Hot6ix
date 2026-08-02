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

/**
 * 판매자 프로필 미등록은 리다이렉트하지 않는다.
 * Figma 에 전용 안내 화면이 있어서 해당 페이지에서 상태로 처리한다.
 */
export function hasSellerProfile(user: SessionUser): boolean {
  return user.sellerProfile !== null
}
