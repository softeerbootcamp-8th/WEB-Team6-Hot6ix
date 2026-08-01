import { useEffect, useRef, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * 네이티브 `<dialog>` 기반 모달.
 *
 * 포커스 트랩·ESC 닫기·배경 비활성화를 브라우저가 처리해줘서 별도
 * 라이브러리를 넣지 않았다.
 */
export function Modal({
  open,
  onClose,
  labelledBy,
  children,
  className,
  /** 처리 중처럼 사용자가 임의로 닫으면 안 되는 상황 */
  dismissible = true,
}: {
  open: boolean
  onClose: () => void
  labelledBy: string
  children: ReactNode
  className?: string
  dismissible?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return

    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return

    const handleCancel = (event: Event) => {
      event.preventDefault()
      if (dismissible) onClose()
    }

    dialog.addEventListener('cancel', handleCancel)
    return () => dialog.removeEventListener('cancel', handleCancel)
  }, [dismissible, onClose])

  return (
    <dialog
      ref={ref}
      aria-labelledby={labelledBy}
      onClick={(event) => {
        // 배경(dialog 자체)을 눌렀을 때만 닫는다.
        if (dismissible && event.target === ref.current) onClose()
      }}
      className={cn(
        // 높이를 제한하지 않으면 내용이 긴 모달(물품 추가 등)이 좁은 화면에서
        // 화면 밖으로 넘쳐 스크롤도 안 된다.
        'm-auto max-h-[calc(100svh-2rem)] w-[calc(100%-2rem)] max-w-[420px] overflow-y-auto rounded-4xl border bg-card p-6 text-foreground',
        'backdrop:bg-neutral-strong/40',
        // 등장·퇴장 트랜지션. 규칙은 globals.css 의 `.modal-pop`.
        'modal-pop',
        className,
      )}
    >
      {children}
    </dialog>
  )
}
