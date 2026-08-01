import { useRouter } from '@tanstack/react-router'

import { cn } from '@/lib/utils'
import {
  MOCK_MEMBER,
  MOCK_SELLER,
  sessionStore,
  useSession,
  type Session,
} from '@/lib/session'

const OPTIONS = [
  { key: 'guest', label: '게스트', apply: () => sessionStore.signOut() },
  {
    key: 'member',
    label: '회원',
    apply: () => sessionStore.signIn(MOCK_MEMBER),
  },
  {
    key: 'seller',
    label: '판매자',
    apply: () => sessionStore.signIn(MOCK_SELLER),
  },
] as const

function currentKey(session: Session) {
  if (session.status === 'guest') return 'guest'
  return session.user.sellerProfile ? 'seller' : 'member'
}

/**
 * 개발용 세션 전환 패널.
 *
 * 화면이 역할(게스트·회원·판매자)에 따라 갈리는데 로그인 API 가 아직 없어서,
 * 목업 세션을 손으로 바꿔가며 확인할 수 있게 둔다. 프로덕션에서는 렌더링하지
 * 않으며, 인증이 붙으면 통째로 삭제한다.
 */
export function DevSessionSwitcher() {
  const session = useSession()
  const router = useRouter()

  if (import.meta.env.PROD) return null

  const active = currentKey(session)

  return (
    <div className="fixed right-4 bottom-4 z-50 flex items-center gap-1 rounded-full border bg-card p-1 shadow-sm">
      <span className="px-2 text-badge font-extrabold text-neutral-muted">
        DEV
      </span>
      {OPTIONS.map((option) => (
        <button
          key={option.key}
          type="button"
          onClick={() => {
            option.apply()
            // 세션이 바뀌면 라우트 가드를 다시 태운다.
            void router.invalidate()
          }}
          aria-pressed={active === option.key}
          className={cn(
            'rounded-full px-3 py-1 text-caption font-semibold transition-colors',
            active === option.key
              ? 'bg-brand-500 text-white'
              : 'text-neutral-tertiary hover:bg-fill',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
