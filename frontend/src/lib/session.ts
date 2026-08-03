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
  /** 프로필 카드에 한 줄로 노출되는 가게 소개. */
  introduction: string
  /** 전화번호 인증까지 마친 판매자. 카드에 "인증 완료" 배지가 붙는다. */
  verified: boolean
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
    introduction: '희소성 있는 스니커즈를 엄선해 소개합니다.',
    verified: true,
  },
}

/** 저장된 세션. 한 번도 저장한 적이 없으면 `null` (게스트로 저장한 것과 구분한다). */
function read(): Session | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

/**
 * 저장된 세션이 없을 때 무엇으로 시작할지.
 *
 * 시연용으로 `VITE_DEMO_AUTOLOGIN=on` 이면 목업 판매자로 시작한다. 프로덕션
 * 빌드에는 DEV 패널이 없어서 로그인할 방법이 사실상 없고, 그러면 내 경매방·
 * 거래 내역·판매자 화면이 전부 `/` 로 튕긴다.
 *
 * **저장된 값이 아예 없을 때만** 적용한다. 로그아웃을 누르면 게스트 상태가
 * 저장되므로 새로고침해도 다시 로그인되지 않는다 — 게스트 화면도 보여줄 수 있다.
 *
 * 실제 로그인이 붙으면 이 함수와 플래그를 지운다.
 */
function initialSession(): Session {
  if (import.meta.env.VITE_DEMO_AUTOLOGIN === 'on') {
    return { status: 'member', user: MOCK_SELLER }
  }
  return GUEST
}

let current: Session = read() ?? initialSession()
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
