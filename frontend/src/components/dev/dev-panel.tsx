import { useRouter } from '@tanstack/react-router'
import { ChevronDown, FlaskConical } from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/lib/utils'
import { devFlagsStore, useDevFlags } from '@/lib/dev-flags'
import {
  MOCK_MEMBER,
  sessionStore,
  useSession,
  type Session,
} from '@/lib/session'

/**
 * 개발용 패널.
 *
 * 화면이 역할(게스트·회원·판매자)에 따라 갈리고, API 를 붙일 때는 로딩·실패
 * 상태도 봐야 한다. 로그인 API 도 백엔드도 없이 그 상황을 만들 수 있게 둔다.
 *
 * - 세션: 게스트 / 회원 전환 (라우트 가드 재실행까지). 판매자 여부는 서버가
 *   `GET /seller-profiles/me` 로 정하므로 여기서 흉내 내지 않는다.
 * - 응답 지연: 모든 API 요청에 지연을 걸어 로딩 UI 확인
 * - 실패율: 요청을 강제로 실패시켜 에러 UI·재시도 확인
 *
 * 프로덕션 빌드에서는 렌더링되지 않으며, `⌘/Ctrl + Shift + D` 로 숨길 수 있다.
 * 인증과 API 가 모두 붙으면 이 폴더는 통째로 지운다.
 */
const SESSIONS = [
  { key: 'guest', label: '게스트', apply: () => sessionStore.signOut() },
  {
    key: 'member',
    label: '회원',
    apply: () => sessionStore.signIn(MOCK_MEMBER),
  },
] as const

const DELAYS = [
  { value: 0, label: '없음' },
  { value: 600, label: '0.6초' },
  { value: 2000, label: '2초' },
]

const FAIL_RATES = [
  { value: 0, label: '없음' },
  { value: 50, label: '50%' },
  { value: 100, label: '항상' },
]

function currentKey(session: Session) {
  return session.status === 'guest' ? 'guest' : 'member'
}

export function DevPanel() {
  const session = useSession()
  const router = useRouter()
  const flags = useDevFlags()
  const [open, setOpen] = useState(false)

  if (import.meta.env.PROD) return null

  const active = currentKey(session)
  const simulating = flags.delayMs > 0 || flags.failRate > 0

  return (
    <div className="fixed right-4 bottom-4 z-50 w-[248px] rounded-2xl border bg-card shadow-lg">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex w-full items-center gap-2 px-3 py-2.5"
      >
        <FlaskConical
          aria-hidden
          className={cn(
            'size-4 shrink-0',
            simulating ? 'text-live' : 'text-neutral-muted',
          )}
        />
        <span className="text-[11px] font-extrabold tracking-wide text-neutral-muted">
          DEV
        </span>
        <span className="ml-auto truncate text-[11px] font-bold text-neutral-tertiary">
          {SESSIONS.find((item) => item.key === active)?.label}
          {simulating && ' · 시뮬레이션'}
        </span>
        <ChevronDown
          aria-hidden
          className={cn(
            'ease-soft size-3.5 shrink-0 text-neutral-muted transition-transform duration-150',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <div className="border-t px-3 py-3">
          <Row label="세션">
            {SESSIONS.map((option) => (
              <Chip
                key={option.key}
                active={active === option.key}
                onClick={() => {
                  option.apply()
                  // 세션이 바뀌면 라우트 가드를 다시 태운다.
                  void router.invalidate()
                }}
              >
                {option.label}
              </Chip>
            ))}
          </Row>

          <Row label="응답 지연">
            {DELAYS.map((option) => (
              <Chip
                key={option.value}
                active={flags.delayMs === option.value}
                onClick={() => devFlagsStore.set({ delayMs: option.value })}
              >
                {option.label}
              </Chip>
            ))}
          </Row>

          <Row label="요청 실패">
            {FAIL_RATES.map((option) => (
              <Chip
                key={option.value}
                active={flags.failRate === option.value}
                onClick={() => devFlagsStore.set({ failRate: option.value })}
              >
                {option.label}
              </Chip>
            ))}
          </Row>

          <p className="mt-3 text-[10px] leading-[1.5] font-medium text-neutral-muted">
            지연·실패는 Orval 훅으로 나가는 모든 요청에 적용됩니다. 목업 화면은
            영향을 받지 않아요. 이 패널은 ⌘/Ctrl + Shift + D 로 숨깁니다.
          </p>
        </div>
      )}
    </div>
  )
}

function Row({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="mb-2.5 last:mb-0">
      <p className="text-[10px] font-bold text-neutral-muted">{label}</p>
      <div className="mt-1 flex gap-1">{children}</div>
    </div>
  )
}

function Chip({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'ease-soft h-7 flex-1 rounded-lg text-[11px] font-bold transition-all duration-150 active:scale-95',
        active
          ? 'bg-brand-500 text-white'
          : 'bg-fill text-neutral-tertiary hover:text-neutral-secondary',
      )}
    >
      {children}
    </button>
  )
}
