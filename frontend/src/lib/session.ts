import { useSyncExternalStore } from 'react'

/**
 * 목업 세션 스토어.
 *
 * 실제 인증은 서버 세션 + HttpOnly 쿠키로 처리하므로, 프론트가 토큰을 들고
 * 있지 않는다. 여기 상태는 **화면 분기용 힌트**일 뿐이고 권한의 근거가 아니다.
 * API 연동 시 이 파일은 `GET /api/v1/users/me` 결과를 담는 얇은 캐시로 바뀐다.
 *
 * React 밖(라우트 `beforeLoad`)에서도 읽어야 해서 모듈 레벨 스토어로 둔다.
 */

export interface SellerProfile {
  shopName: string
  snsUrl: string
  contact: string
}

export interface SessionUser {
  id: number
  nickname: string
  kakaoEmail: string
  /** 전화번호 인증을 마쳐야 입찰할 수 있다. */
  phone: string | null
  sellerProfile: SellerProfile | null
}

export type Session =
  { status: 'guest' } | { status: 'member'; user: SessionUser }

const STORAGE_KEY = 'upbid.mock-session'

const GUEST: Session = { status: 'guest' }

/** 개발 중 로그인 상태를 확인할 때 쓰는 기본 회원. */
export const MOCK_MEMBER: SessionUser = {
  id: 1,
  nickname: '기승민',
  kakaoEmail: 'seungmin@kakao.com',
  phone: '010-1234-5678',
  sellerProfile: null,
}

/** 판매자 프로필까지 등록한 상태. */
export const MOCK_SELLER: SessionUser = {
  ...MOCK_MEMBER,
  sellerProfile: {
    shopName: '승민이네 빈티지',
    snsUrl: 'https://instagram.com/upbid',
    contact: '010-1234-5678',
  },
}

function read(): Session {
  if (typeof window === 'undefined') return GUEST
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : GUEST
  } catch {
    return GUEST
  }
}

let current: Session = read()
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

export const sessionStore = {
  getState: (): Session => current,

  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },

  set(next: Session) {
    current = next
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    } catch {
      // 저장 실패해도 메모리 상태는 유지한다.
    }
    emit()
  },

  signIn(user: SessionUser = MOCK_MEMBER) {
    sessionStore.set({ status: 'member', user })
  },

  signOut() {
    sessionStore.set(GUEST)
  },
}

export type SessionStore = typeof sessionStore

/** 컴포넌트에서 세션을 구독한다. */
export function useSession(): Session {
  return useSyncExternalStore(
    sessionStore.subscribe,
    sessionStore.getState,
    () => GUEST,
  )
}

/** 로그인 상태면 회원 정보를, 아니면 null 을 준다. */
export function useCurrentUser(): SessionUser | null {
  const session = useSession()
  return session.status === 'member' ? session.user : null
}
