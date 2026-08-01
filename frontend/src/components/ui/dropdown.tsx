import { Check, ChevronDown } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'

import { cn } from '@/lib/utils'

export interface DropdownOption<T extends string> {
  value: T
  label: string
}

/**
 * 우리 화면용 드롭다운.
 *
 * 네이티브 `<select>` 는 글자 크기·간격을 브라우저가 정해서 다른 컨트롤과
 * 어긋나 보였다(특히 macOS 에서 작게 나온다). 목록을 직접 그려서 다른
 * 버튼·입력과 같은 높이·글자 크기를 쓰게 한다.
 *
 * 키보드: `Enter`/`Space` 로 열고, `Esc` 로 닫고, ↑↓ 로 이동한다.
 * 바깥을 누르면 닫히고, 포커스는 트리거로 돌아온다.
 */
export function Dropdown<T extends string>({
  value,
  options,
  onChange,
  label,
  className,
}: {
  value: T
  options: DropdownOption<T>[]
  onChange: (next: T) => void
  /** 스크린리더용 이름 */
  label: string
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(() =>
    Math.max(
      0,
      options.findIndex((option) => option.value === value),
    ),
  )
  const rootRef = useRef<HTMLDivElement>(null)
  const listId = useId()

  const selected = options.find((option) => option.value === value)

  // 바깥 클릭으로 닫기. 열려 있을 때만 듣는다.
  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }

    window.addEventListener('pointerdown', onPointerDown)
    return () => window.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  const choose = (next: T) => {
    onChange(next)
    setOpen(false)
  }

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={label}
        onClick={() => setOpen((prev) => !prev)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            setOpen(true)
          }
        }}
        className="ease-soft flex h-10 w-full items-center gap-2 rounded-xl border bg-card px-3.5 text-[14px] font-semibold text-foreground transition-all duration-150 hover:border-border-strong focus-visible:border-brand-400 focus-visible:outline-none active:scale-[0.99]"
      >
        <span className="min-w-0 flex-1 truncate text-left">
          {selected?.label}
        </span>
        <ChevronDown
          aria-hidden
          className={cn(
            'ease-soft size-4 shrink-0 text-neutral-tertiary transition-transform duration-150',
            open && 'rotate-180',
          )}
        />
      </button>

      {open && (
        <ul
          id={listId}
          role="listbox"
          aria-label={label}
          tabIndex={-1}
          // 목록이 열리면 바로 키보드를 받는다.
          ref={(node) => node?.focus()}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault()
              setOpen(false)
              return
            }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
              event.preventDefault()
              setActive((prev) => {
                const step = event.key === 'ArrowDown' ? 1 : -1
                return (prev + step + options.length) % options.length
              })
              return
            }
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              const option = options[active]
              if (option) choose(option.value)
            }
          }}
          className="animate-rise absolute top-[calc(100%+6px)] right-0 left-0 z-30 max-h-64 overflow-y-auto rounded-xl border bg-card p-1 shadow-lg outline-none"
        >
          {options.map((option, index) => {
            const isSelected = option.value === value

            return (
              <li key={option.value}>
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => choose(option.value)}
                  className={cn(
                    'ease-soft flex h-10 w-full items-center gap-2 rounded-lg px-3 text-left text-[14px] transition-colors duration-150',
                    isSelected
                      ? 'bg-brand-50 font-bold text-brand-600'
                      : 'font-medium text-neutral-secondary',
                    index === active && !isSelected && 'bg-fill',
                  )}
                >
                  <span className="min-w-0 flex-1 truncate">
                    {option.label}
                  </span>
                  {isSelected && (
                    <Check aria-hidden className="size-4 shrink-0" />
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
