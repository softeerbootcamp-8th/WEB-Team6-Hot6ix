import { cn } from '@/lib/utils'

/** 카카오 심볼. 규격상 버튼 안에서 라벨과 함께 쓴다. */
function KakaoSymbol() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 18 18"
      className="size-[18px] shrink-0"
      fill="currentColor"
    >
      <path d="M9 1C4.58 1 1 3.79 1 7.23c0 2.2 1.47 4.13 3.68 5.22-.16.57-.59 2.12-.67 2.45-.11.41.15.4.31.29.13-.08 2.05-1.39 2.88-1.96.58.08 1.18.13 1.8.13 4.42 0 8-2.79 8-6.13S13.42 1 9 1Z" />
    </svg>
  )
}

/**
 * 카카오 로그인 버튼.
 *
 * 배경 `#FEE500` · 라벨 검정 85%는 카카오 로그인 디자인 가이드 값이라
 * 우리 브랜드 토큰이 아니라 별도 토큰(`--kakao`)으로 관리한다.
 */
export function KakaoLoginButton({
  onClick,
  pending = false,
  className,
}: {
  onClick?: () => void
  pending?: boolean
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={pending}
      className={cn(
        'flex h-14 w-full items-center justify-center gap-2 rounded-2xl',
        'bg-kakao text-card-title font-bold text-kakao-foreground',
        'transition-opacity hover:opacity-90',
        'focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-60',
        className,
      )}
    >
      <KakaoSymbol />
      {pending ? '카카오로 이동 중…' : '카카오로 계속하기'}
    </button>
  )
}
