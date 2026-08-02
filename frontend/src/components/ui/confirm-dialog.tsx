import { useId, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'

/**
 * 확인 다이얼로그.
 *
 * 되돌릴 수 없는 동작(거래 성사·실패 확정, 삭제, 탈퇴) 앞에 세운다.
 * `Modal` 위에 제목·설명·취소/확인만 얹은 얇은 래퍼라, 화면마다 같은 마크업을
 * 다시 쓰지 않아도 된다.
 *
 * 처리 중(`pending`)에는 배경·ESC 로 닫히지 않는다. 서버 응답 전에 사용자가
 * 실수로 닫아 상태가 어긋나는 걸 막는다.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '확인',
  cancelLabel = '취소',
  tone = 'brand',
  pending = false,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  description?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  /** 되돌릴 수 없거나 파괴적인 동작이면 `danger`. */
  tone?: 'brand' | 'danger'
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const titleId = useId()

  return (
    <Modal
      open={open}
      onClose={onCancel}
      labelledBy={titleId}
      dismissible={!pending}
    >
      <h2 id={titleId} className="text-[17px] font-bold text-foreground">
        {title}
      </h2>

      {description && (
        <div className="mt-3 text-[14px] font-medium text-neutral-tertiary">
          {description}
        </div>
      )}

      <div className="mt-6 flex gap-2">
        <Button
          type="button"
          variant="outline"
          size="lg"
          className="h-12 flex-1 rounded-xl"
          disabled={pending}
          onClick={onCancel}
        >
          {cancelLabel}
        </Button>
        <Button
          type="button"
          variant={tone === 'danger' ? 'danger' : 'brand'}
          size="lg"
          className="h-12 flex-1 rounded-xl"
          disabled={pending}
          onClick={onConfirm}
        >
          {pending ? '처리 중…' : confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
