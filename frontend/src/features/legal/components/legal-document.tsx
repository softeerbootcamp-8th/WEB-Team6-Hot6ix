import { formatDate } from '@/lib/format'

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

      <p className="mt-10 rounded-2xl bg-surface-subtle px-5 py-4 text-caption font-normal text-neutral-muted">
        본 문서는 초안입니다. 확정 전까지 내용이 바뀔 수 있습니다.
      </p>
    </article>
  )
}
