import { Link } from '@tanstack/react-router'
import { AlertTriangle, Compass, RotateCcw } from 'lucide-react'
import type { ReactNode } from 'react'

import { GuestShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useSession } from '@/lib/session'

/**
 * 라우트 공통 상태 화면 3종.
 *
 * `main.tsx` 에서 `defaultNotFoundComponent` / `defaultErrorComponent` /
 * `defaultPendingComponent` 로 한 번만 등록한다. 라우트마다 만들지 않는다.
 * 특정 라우트에서 다르게 보여줘야 하면 그 라우트의 `notFoundComponent`
 * 등으로 덮어쓴다.
 *
 * 구성은 Figma `WEB-01 · 판매자 · 프로필 미등록`(847:12938)의 안내 카드를
 * 그대로 따른다 — 640 카드, 80 원형 아이콘, 24 제목, 설명,
 * 옅은 정보 박스, CTA. 이 톤을 벗어나지 않는 게 목적이다.
 */

/** 안내 카드 한 장. 세 화면이 같은 뼈대를 쓴다. */
function NoticeCard({
  tone,
  icon,
  title,
  description,
  facts,
  actions,
}: {
  tone: 'brand' | 'live'
  icon: ReactNode
  title: string
  description: string
  /** 옅은 박스에 들어가는 보조 안내. 지금 할 수 있는 걸 알려준다. */
  facts?: { label: string; value: string }
  actions: ReactNode
}) {
  return (
    <section className="animate-rise mx-auto w-full max-w-[640px] rounded-[24px] border bg-card px-5 pt-9 pb-9 text-center md:px-14 md:pt-12 md:pb-12">
      <span
        aria-hidden
        className={cn(
          'mx-auto flex size-16 items-center justify-center rounded-full md:size-20',
          tone === 'brand'
            ? 'bg-brand-50 text-brand-500'
            : 'bg-live-surface text-live',
        )}
      >
        {icon}
      </span>

      <h1 className="mt-5 text-[20px] font-extrabold text-foreground md:mt-6 md:text-[24px]">
        {title}
      </h1>
      <p className="mt-3 text-[13px] leading-[1.6] font-medium whitespace-pre-line text-neutral-tertiary md:mt-4 md:text-[14px]">
        {description}
      </p>

      {facts && (
        <div
          className={cn(
            'mt-5 rounded-[18px] px-5 py-4 md:mt-6 md:px-6 md:py-[18px]',
            tone === 'brand' ? 'bg-brand-50' : 'bg-live-surface',
          )}
        >
          <p
            className={cn(
              'text-[13px] font-bold',
              tone === 'brand' ? 'text-brand-500' : 'text-live',
            )}
          >
            {facts.label}
          </p>
          <p className="mt-3 text-[13px] font-semibold text-foreground">
            {facts.value}
          </p>
        </div>
      )}

      <div className="mt-7 flex flex-col items-center gap-2 md:mt-8 md:gap-3">
        {actions}
      </div>
    </section>
  )
}

/** 로그인 상태에 따라 "돌아갈 곳"이 달라진다. */
function useHome() {
  const session = useSession()
  return session.status === 'member'
    ? ({ to: '/rooms', label: '내 경매방으로' } as const)
    : ({ to: '/', label: '처음으로' } as const)
}

/** 없는 URL. 주소 오타나 삭제된 리소스로 들어왔을 때. */
export function RouteNotFound() {
  const home = useHome()

  return (
    <GuestShell title="페이지를 찾을 수 없어요" className="max-w-[1280px]">
      <NoticeCard
        tone="brand"
        icon={<Compass className="size-8 md:size-9" />}
        title="페이지를 찾을 수 없어요"
        description={
          '주소가 바뀌었거나 삭제된 화면일 수 있어요.\n링크를 다시 확인해 주세요.'
        }
        facts={{
          label: '이럴 때 나와요',
          value: '주소 오타 · 만료된 공유 링크 · 삭제된 경매방',
        }}
        actions={
          <>
            <Button
              asChild
              variant="brand"
              size="form"
              className="w-full max-w-[360px]"
            >
              <Link to={home.to}>{home.label}</Link>
            </Button>
            <Button
              asChild
              variant="ghost"
              size="field"
              className="text-neutral-tertiary"
            >
              <Link to="/rooms">참여 경매방 목록 보기</Link>
            </Button>
          </>
        }
      />
    </GuestShell>
  )
}

/**
 * 렌더·로더에서 던진 에러.
 *
 * 사용자에게 원문 메시지를 그대로 보여주지 않는다. 서버 내부 정보가
 * 섞여 나올 수 있어서다. 개발 중에만 콘솔로 확인할 수 있게 둔다.
 */
export function RouteError({
  error,
  reset,
}: {
  error: Error
  reset: () => void
}) {
  const home = useHome()

  if (import.meta.env.DEV) console.error(error)

  return (
    <GuestShell title="문제가 생겼어요" className="max-w-[1280px]">
      <NoticeCard
        tone="live"
        icon={<AlertTriangle className="size-8 md:size-9" />}
        title="화면을 불러오지 못했어요"
        description={
          '일시적인 문제일 수 있어요.\n다시 시도해도 같은 화면이 나오면 알려주세요.'
        }
        facts={{
          label: '먼저 확인해 주세요',
          value: '네트워크 연결 · 잠시 후 재시도',
        }}
        actions={
          <>
            <Button
              type="button"
              variant="brand"
              size="form"
              onClick={reset}
              className="w-full max-w-[360px]"
            >
              <RotateCcw aria-hidden className="size-4" />
              다시 시도
            </Button>
            <Button
              asChild
              variant="ghost"
              size="field"
              className="text-neutral-tertiary"
            >
              <Link to={home.to}>{home.label}</Link>
            </Button>
          </>
        }
      />
    </GuestShell>
  )
}

/**
 * 로더가 도는 동안.
 *
 * 스피너 하나만 띄우면 화면이 텅 비어 보여서, 실제로 들어올 레이아웃
 * (상단바 + 카드)의 골격을 옅게 깔아 둔다. 자리 이동이 덜 느껴진다.
 * `defaultPendingMs` 를 넘겨야 뜨므로 짧은 요청에서는 보이지 않는다.
 */
export function RoutePending() {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      className="min-h-svh bg-background"
    >
      <span className="sr-only">불러오는 중</span>

      {/* 상단바 골격 */}
      <div className="h-16 border-b bg-card" />

      <div className="mx-auto w-full max-w-[1280px] px-5 py-6 md:px-7 md:py-10">
        <div className="animate-skeleton h-7 w-40 rounded-lg bg-fill" />
        <div
          className="animate-skeleton mt-3 h-4 w-64 rounded-md bg-fill"
          style={{ animationDelay: '80ms' }}
        />

        <div className="mt-6 grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
          {[0, 1].map((index) => (
            <div
              key={index}
              className="animate-skeleton h-[180px] rounded-[24px] border bg-card md:h-[280px]"
              style={{ animationDelay: `${160 + index * 80}ms` }}
            />
          ))}
        </div>
      </div>
    </div>
  )
}
