import { X } from 'lucide-react'
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
  /**
   * 우상단에 닫기(X) 버튼을 띄운다.
   *
   * 기본은 꺼져 있다. `ConfirmDialog` 처럼 "취소" 버튼이 이미 있는 모달에서는
   * 같은 일을 하는 버튼이 둘이 되기 때문이다. 큰 모달처럼 ESC·배경 클릭
   * 말고는 닫는 법이 눈에 안 보이는 곳에서만 켠다.
   */
  closeButton = false,
}: {
  open: boolean
  onClose: () => void
  labelledBy: string
  children: ReactNode
  className?: string
  dismissible?: boolean
  closeButton?: boolean
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
        if (!dismissible) return

        /*
         * 커서가 모달 상자 **바깥**일 때만 닫는다.
         *
         * `event.target === dialog` 로 판정하면 안 된다. 제목과 본문 사이 여백,
         * dialog 자신의 padding 처럼 **자식 요소가 없는 안쪽 빈 공간**도 target 이
         * dialog 라서, 모달 내부를 눌렀는데 닫히는 것으로 취급된다.
         * 물품 추가처럼 여러 개를 고르는 모달에서는 고르던 게 통째로 날아간다.
         */
        const rect = ref.current?.getBoundingClientRect()
        if (!rect) return

        const outside =
          event.clientX < rect.left ||
          event.clientX > rect.right ||
          event.clientY < rect.top ||
          event.clientY > rect.bottom

        if (outside) onClose()
      }}
      className={cn(
        /*
         * 닫혀 있으면 반드시 사라져야 한다.
         * 브라우저 기본 규칙은 `dialog:not([open]) { display: none }` 인데,
         * 바깥에서 `flex` 같은 display 유틸을 얹으면 그 규칙을 이겨서
         * **닫힌 모달이 화면 밖에 그대로 남고 페이지가 그만큼 길어진다.**
         */
        '[&:not([open])]:hidden',
        /*
         * **`relative` 를 얹지 않는다.**
         *
         * `showModal()` 로 연 dialog 는 top layer 로 올라가고, 브라우저가
         * `position: fixed` 를 준다 — 스크롤을 얼마나 내렸든 지금 보는 화면
         * 정가운데에 뜬다는 뜻이다. 그런데 top layer 요소에 `static`/`relative`
         * 를 지정하면 명세상 `absolute` 로 바뀌면서 그 `fixed` 가 사라지고,
         * 모달이 문서 위쪽에 남는다. 긴 화면(모바일 경매방 생성)에서 물품 추가를
         * 열면 한참 위로 스크롤해야 보였던 게 이것 때문이다.
         *
         * 닫기 버튼의 기준점도 따로 만들 필요가 없다. `fixed` 자체가 positioned
         * 라서 아래 `absolute` 버튼이 dialog 를 기준으로 앉는다.
         */
        // 높이를 제한하지 않으면 내용이 긴 모달(물품 추가 등)이 좁은 화면에서
        // 화면 밖으로 넘쳐 스크롤도 안 된다.
        'm-auto max-h-[calc(100svh-2rem)] w-[calc(100%-2rem)] max-w-[420px] overflow-y-auto rounded-4xl border bg-card p-6 text-foreground',
        'backdrop:bg-neutral-strong/40',
        // 등장·퇴장 트랜지션. 규칙은 globals.css 의 `.modal-pop`.
        'modal-pop',
        className,
      )}
    >
      {/* 처리 중에는 배경 클릭·ESC 와 함께 이 버튼도 사라져야 한다. */}
      {closeButton && dismissible && (
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="ease-soft absolute top-4 right-4 z-10 flex size-8 items-center justify-center rounded-full border bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
        >
          <X aria-hidden className="size-4" />
        </button>
      )}

      {children}
    </dialog>
  )
}
