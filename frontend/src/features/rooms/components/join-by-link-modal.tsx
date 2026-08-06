import { useNavigate } from '@tanstack/react-router'
import { useEffect, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { TextField } from '@/components/ui/field'

/**
 * 초대 링크·코드로 경매방에 들어가는 모달.
 *
 * 예전에는 목록 화면의 버튼이 `shareCode: 'abc123'` 을 **하드코딩**해서
 * 누르면 무조건 "유효하지 않은 링크예요"가 떴다. 목업 시절 잔재였다.
 *
 * QR 을 못 찍고 링크만 받은 사람에게는 이게 유일한 진입 수단이라, 버튼을
 * 없애는 대신 실제로 코드를 받도록 바꿨다.
 */
export function JoinByLinkModal({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const navigate = useNavigate()
  const [input, setInput] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setInput('')
    setError(null)
  }, [open])

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()

    const shareCode = toShareCode(input)
    if (!shareCode) {
      setError('링크나 참여 코드를 다시 확인해 주세요.')
      return
    }

    onClose()
    void navigate({ to: '/join/$shareCode', params: { shareCode } })
  }

  return (
    <Modal open={open} onClose={onClose} labelledBy="join-by-link-title">
      <h2
        id="join-by-link-title"
        className="text-[17px] font-bold text-foreground"
      >
        초대 링크로 참여하기
      </h2>
      <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
        받은 링크를 통째로 붙여넣거나 참여 코드만 입력하세요.
      </p>

      <form onSubmit={handleSubmit} className="mt-6">
        <TextField
          label="초대 링크 또는 참여 코드"
          value={input}
          error={error ?? undefined}
          autoFocus
          placeholder="https://upbid.site/join/AB12CD"
          onChange={(event) => {
            setInput(event.target.value)
            setError(null)
          }}
        />

        <div className="mt-6 flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="h-12 flex-1 rounded-xl"
            onClick={onClose}
          >
            취소
          </Button>
          <Button
            type="submit"
            variant="brand"
            size="lg"
            className="h-12 flex-1 rounded-xl"
            disabled={!input.trim()}
          >
            참여하기
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * 붙여넣은 값에서 공유 코드만 뽑는다.
 *
 * 주소창에서 복사하면 `?utm=...` 이나 끝 슬래시가 딸려 오고, 카카오톡에서
 * 복사하면 코드만 오기도 한다. 둘 다 같은 방으로 가야 한다.
 *
 * 서버가 발급하는 코드는 영문·숫자다(`share_code`, 32자 이하). 그 밖의 글자가
 * 섞여 있으면 링크를 잘못 복사한 것으로 보고 되돌려 보낸다.
 */
function toShareCode(raw: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null

  const fromUrl = trimmed.match(/\/join\/([^/?#]+)/)
  const candidate = decodeURIComponent(fromUrl ? fromUrl[1] : trimmed)

  return /^[A-Za-z0-9]{1,32}$/.test(candidate) ? candidate : null
}
