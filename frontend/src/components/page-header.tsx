import type { ReactNode } from 'react'

/** 화면 상단 제목 영역. 웹에서만 보이고 모바일은 앱바 제목을 쓴다. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-[20px] font-bold text-foreground">{title}</h1>
        {description && (
          <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
            {description}
          </p>
        )}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

/** 비어 있는 목록 자리. 무엇을 하면 되는지까지 알려준다. */
export function EmptyState({
  icon,
  title,
  description,
  hint,
  action,
}: {
  icon?: ReactNode
  title: string
  description?: string
  hint?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center rounded-4xl border bg-card px-6 py-16 text-center">
      {icon && (
        <span
          aria-hidden
          className="mb-6 flex size-20 items-center justify-center rounded-full bg-brand-50 text-[28px] font-extrabold text-brand-500"
        >
          {icon}
        </span>
      )}
      <h2 className="text-[20px] font-extrabold text-foreground md:text-[22px]">
        {title}
      </h2>
      {description && (
        <p className="mt-3 text-[14px] font-medium text-neutral-tertiary">
          {description}
        </p>
      )}
      {hint && (
        <p className="mt-6 text-[13px] font-semibold text-neutral-muted">
          {hint}
        </p>
      )}
      {action && <div className="mt-8">{action}</div>}
    </div>
  )
}
