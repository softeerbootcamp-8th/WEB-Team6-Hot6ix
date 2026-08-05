import { useSyncExternalStore } from 'react'

import { axiosInstance } from '@/api/mutator/custom-instance'

/**
 * 로그인 세션 스토어.
 *
 * 실제 인증은 서버 세션 + HttpOnly 쿠키로 처리하므로, 프론트가 토큰을 들고
 * 있지 않는다. 여기 상태는 **화면 분기용 힌트**일 뿐이고 권한의 근거가 아니다.
 * 값은 `__root.tsx` 가 앱 로드 때 `GET /api/v1/users/me` 로 채운다.
 *
 * React 밖(라우트 `beforeLoad`)에서도 읽어야 해서 모듈 레벨 스토어로 둔다.
 *
 * **판매자 프로필은 여기 두지 않는다.** 서버 상태라 TanStack Query
 * (`useGetMyProfile`)가 소스이고, 여기 복사해 두면 출처가 둘이 된다.
 */

export interface SessionUser {
  id: number
  nickname: string
  kakaoEmail: string
  /** 전화번호 인증을 마쳐야 입찰할 수 있다. */
  phone: string | null
  /** `/users/me` 의 `profileImageUrl`. 올린 적 없으면 null 이다. */
  profileImageUrl: string | null
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
  profileImageUrl: null,
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

/** `GET /api/v1/users/me` 응답 DTO. 서버 `UserResponseDto` 와 필드를 맞춘다. */
interface MeResponseData {
  userId: number
  nickname: string
  email: string
  profileImageUrl: string | null
}

interface MeResponse {
  success: boolean
  data: MeResponseData
}

/**
 * `GET /api/v1/users/me` 를 호출해 세션을 채운다. 로그인 세션(쿠키)이 있어야
 * 성공한다 — 앱 초기화(`__root.tsx`)와 카카오 회원가입 직후(온보딩 완료) 양쪽에서 쓴다.
 *
 * `phone` 은 이 응답에 없다. 알고 있으면 인자로 넘기고, 모르면 기존 세션값을
 * 유지한다 (백엔드가 `/me` 에 phone 을 추가하면 이 인자를 없앤다).
 *
 * TanStack Query 의 `queryFn` 으로도 쓰이므로 값을 반환해야 한다 — `undefined` 를
 * 반환하면 "Query data cannot be undefined" 로 fetchQuery 가 실패 처리되고,
 * 방금 채운 세션이 `__root.tsx` 의 catch(`sessionStore.signOut()`)로 되돌아간다.
 */
export async function hydrateSession(
  phone?: string | null,
): Promise<SessionUser> {
  const prev = sessionStore.getState()
  const prevPhone = prev.status === 'member' ? prev.user.phone : null

  const { data } = await axiosInstance.get<MeResponse>('/api/v1/users/me')

  const user: SessionUser = {
    id: data.data.userId,
    nickname: data.data.nickname,
    kakaoEmail: data.data.email,
    phone: phone ?? prevPhone,
    profileImageUrl: data.data.profileImageUrl ?? null,
  }

  sessionStore.signIn(user)
  return user
}
