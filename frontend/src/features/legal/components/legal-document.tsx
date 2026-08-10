import type { ReactNode } from 'react'

import { AppShell, GuestShell } from '@/components/layout/page-shell'
import { formatDate } from '@/lib/format'
import { useCurrentUser } from '@/lib/session'

/**
 * 약관·방침 화면의 골격.
 *
 * 약관은 비로그인도 볼 수 있어야 해서 `requireMember` 를 붙이지 않는다. 대신
 * 상단만 갈라 그린다 — 예전에는 두 라우트가 `GuestShell` 에 `state="비로그인"`
 * 을 고정으로 넘겨서, 로그인한 사람이 약관을 열면 상단이 "비로그인" 으로
 * 바뀌었다. 모바일에서는 같은 줄의 `MobileNavDrawer` 가 로그인한 사람에게만
 * 프로필 사진을 그려서, "비로그인" 알약과 내 사진이 같이 떴다.
 */
export function LegalShell({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  const user = useCurrentUser()

  if (user) {
    return (
      <AppShell title={title} back className="max-w-[720px]">
        {children}
      </AppShell>
    )
  }

  return (
    <GuestShell title={title} back state="비로그인" className="max-w-[720px]">
      {children}
    </GuestShell>
  )
}

/**
 * 약관·방침 공통 문서 틀.
 *
 * 본문은 법무 확정 전 초안이다. 확정본이 나오면 각 라우트의 `SECTIONS` 만
 * 교체하면 된다.
 */
export function LegalDocument({
  title,
  updatedAt,
  sections,
}: {
  title: string
  updatedAt: string
  sections: { title: string; body: string }[]
}) {
  return (
    <article className="rounded-4xl border bg-card p-6 md:p-8">
      <h1 className="text-[22px] font-extrabold text-foreground md:text-[24px]">
        {title}
      </h1>
      <p className="mt-2 text-caption font-normal text-neutral-muted">
        시행일 {formatDate(updatedAt)}
      </p>

      <div className="mt-8 space-y-7">
        {sections.map((section, index) => (
          <section key={section.title}>
            <h2 className="text-card-title font-bold text-foreground">
              {index + 1}. {section.title}
            </h2>
            <p className="mt-2.5 text-label leading-[1.8] font-normal text-neutral-tertiary">
              {section.body}
            </p>
          </section>
        ))}
      </div>
    </article>
  )
}
