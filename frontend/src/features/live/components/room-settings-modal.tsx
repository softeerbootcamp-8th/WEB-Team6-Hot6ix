import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { NumberField, TextAreaField, TextField } from '@/components/ui/field'
import {
  getGetMyRoomsQueryKey,
  getGetRoomByShareCodeQueryKey,
  useUpdate2,
} from '@/api/generated/경매방/경매방'
import { toAuctionRoomErrorMessage } from '@/features/seller/auction-room-error'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import {
  ImageUploadError,
  useImageUpload,
} from '@/features/seller/use-image-upload'
import { PresignedUrlRequestDtoDomain } from '@/api/generated/model'
import { toast } from '@/lib/toast'
import {
  MAX_SOFT_CLOSE_MINUTES,
  MIN_SOFT_CLOSE_MINUTES,
  softCloseMinutesError,
} from '@/features/rooms/soft-close'
import type {
  AuctionRoomPublicResponseDto,
  AuctionRoomUpdateRequestDto,
} from '@/api/generated/model'

/**
 * 경매방 설정 수정 (이슈 #140).
 *
 * **화면이 아니라 모달이다.** 라우트를 옮기면 경매방이 언마운트되면서 실시간
 * 연결·타이머·쌓아둔 이벤트가 전부 끊긴다(`UI-RULES.md` 3장). 물품 상세를
 * overlay 로 만든 것과 같은 이유다.
 *
 * 서버가 정한 수정 가능 범위를 그대로 비춘다.
 *
 * | 요청                    | 시작 전 | 진행 중 | 종료 |
 * | ----------------------- | ------- | ------- | ---- |
 * | 이름 · 방송 링크        | O       | **O**   | X    |
 * | 소개 · Soft Close 설정  | O       | X (409) | X    |
 *
 * 진행 중에 이름과 방송 링크를 열어 둔 건 **둘 다 방송을 켠 뒤에야 잘못이
 * 드러나는 값**이라서다 — 이름은 오타, 방송 링크는 "안 열려요"라는 말을 듣고서야
 * 안다. 그때 잠그면 정작 고쳐야 할 순간에 못 고친다. 나머지는 참여자가 이미
 * 보고 입찰을 판단한 조건이라 바뀌면 안 된다.
 *
 * 화면 잠금은 편의일 뿐이고 **판단 근거는 서버 응답이다** — 폼을 열어 둔 사이
 * 물품이 시작되는 경합에서는 409 를 받아 안내한다.
 */
export function RoomSettingsModal({
  open,
  onClose,
  room,
}: {
  open: boolean
  onClose: () => void
  room: AuctionRoomPublicResponseDto
}) {
  /*
   * 저장 중에는 배경 클릭·ESC 로 닫히면 안 된다. 그 판정이 바깥 `Modal` 몫이라
   * mutation 도 여기서 만들어 폼에 내려준다.
   */
  const updateRoom = useUpdate2()
  // 커버 업로드도 같은 이유로 여기서 만든다. 올리는 동안에도 닫히면 안 된다.
  const cover = useImageUpload(PresignedUrlRequestDtoDomain.AUCTION_ROOM_COVER)

  return (
    <Modal
      open={open}
      onClose={onClose}
      labelledBy="room-settings-title"
      dismissible={!updateRoom.isPending && !cover.uploading}
      className="max-w-[520px]"
    >
      {/*
        폼은 열려 있는 동안에만 마운트한다. 그래야 닫을 때 편집분이 언마운트로
        같이 사라져, 다음에 열면 서버 값에서 다시 시작한다.

        `useEffect` 로 초기화하지 않는 이유가 여기 있다. `room` 은 react-query 가
        준 객체라 **refetch 때마다 정체성이 바뀐다.** 라이브 화면은 SSE 로 방 정보를
        자주 다시 받으므로, `[open, room]` 에 걸어 두면 사용자가 이름을 치는 도중
        refetch 한 번에 입력이 통째로 서버 값으로 되돌아간다.
      */}
      {open && (
        <RoomSettingsForm
          room={room}
          onClose={onClose}
          updateRoom={updateRoom}
          cover={cover}
        />
      )}
    </Modal>
  )
}

function RoomSettingsForm({
  room,
  onClose,
  updateRoom,
  cover,
}: {
  room: AuctionRoomPublicResponseDto
  onClose: () => void
  updateRoom: ReturnType<typeof useUpdate2>
  cover: ReturnType<typeof useImageUpload>
}) {
  const auctionRoomId = room.auctionRoomId ?? 0

  // 마운트 시점의 서버 값이 초깃값이다. 이후 refetch 는 편집분을 건드리지 않는다.
  const [name, setName] = useState(room.name ?? '')
  const [description, setDescription] = useState(room.description ?? '')
  const [liveUrl, setLiveUrl] = useState(room.liveUrl ?? '')
  const [triggerMinutes, setTriggerMinutes] = useState(() =>
    toMinutes(room.softCloseTriggerSeconds),
  )
  const [extendMinutes, setExtendMinutes] = useState(() =>
    toMinutes(room.softCloseExtendSeconds),
  )
  const [error, setError] = useState<string | null>(null)
  const [coverFile, setCoverFile] = useState<File | null>(null)

  const queryClient = useQueryClient()

  /*
   * 경매가 시작되면 서버가 거절하는 필드들. 이름과 방송 링크는 여기서 빠진다 —
   * 둘 다 방송을 켠 뒤에야 잘못이 드러나는 값이라(이름은 오타, 방송 링크는 "안 열려요"),
   * 그때 잠그면 정작 고쳐야 할 순간에 못 고친다.
   */
  const locked = room.status !== 'BEFORE'

  const patch = buildPatch({
    room,
    name,
    description,
    liveUrl,
    triggerMinutes,
    extendMinutes,
    locked,
  })
  // 커버는 고른 파일이 곧 변경이다. 주소는 저장할 때 올려야 나오므로 patch 에 없다.
  const changed = Object.keys(patch).length > 0 || coverFile !== null

  // 커버를 S3 에 올리는 동안도 저장 중이다. 빼면 그 사이 버튼이 살아 있어 두 번
  // 누를 수 있고, 그러면 업로드가 두 번 나간다.
  const saving = cover.uploading || updateRoom.isPending

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()

    if (name.trim().length < 2) {
      setError('경매방 이름을 2자 이상 입력해주세요.')
      return
    }
    /*
     * 서버가 어차피 400 으로 거절하는데, 그때는 커버 이미지가 이미 S3 에 올라간
     * 뒤라 쓰지 않는 파일이 남는다. 값이 잠긴 방(locked)은 애초에 patch 에서 빠진다.
     */
    if (
      !locked &&
      (softCloseMinutesError(triggerMinutes) !== undefined ||
        softCloseMinutesError(extendMinutes) !== undefined)
    ) {
      return
    }
    if (!changed) {
      onClose()
      return
    }

    /*
     * 커버를 먼저 올리고 그 주소를 patch 에 얹는다. 올리다 실패하면 저장 자체를
     * 멈춘다 — 나머지만 저장되면 커버를 바꿨다고 생각하는데 안 바뀐 상태가 된다.
     *
     * 잠긴 방에서는 입력이 disabled 라 `coverFile` 이 null 이다. 그래도 한 번 더
     * 거른다 — 커버가 실려 있으면 서버가 방 전체 수정을 409 로 거절한다.
     */
    let body = patch
    if (coverFile && !locked) {
      try {
        body = { ...patch, coverImageUrl: await cover.uploadImage(coverFile) }
      } catch (caught) {
        toast.error(
          caught instanceof ImageUploadError
            ? caught.message
            : '커버 이미지를 올리지 못했어요.',
          { description: '잠시 뒤에 다시 시도해 주세요.' },
        )
        return
      }
    }

    try {
      await updateRoom.mutateAsync({ roomId: auctionRoomId, data: body })

      void queryClient.invalidateQueries({
        queryKey: getGetRoomByShareCodeQueryKey(room.shareCode ?? ''),
      })
      void queryClient.invalidateQueries({ queryKey: getGetMyRoomsQueryKey() })

      toast.success('경매방 설정을 저장했어요')
      onClose()
    } catch (caught) {
      const message = toAuctionRoomErrorMessage(caught)
      toast.error(message.title, { description: message.description })
    }
  }

  return (
    <>
      <h2
        id="room-settings-title"
        className="text-[17px] font-bold text-foreground"
      >
        경매방 설정
      </h2>
      <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
        {locked
          ? '경매가 시작돼 이름과 방송 링크만 고칠 수 있어요.'
          : '참여자에게 보이는 정보예요. 경매가 시작되면 이름과 방송 링크 말고는 고칠 수 없어요.'}
      </p>

      <form onSubmit={(event) => void handleSubmit(event)} className="mt-6">
        <TextField
          label="경매방 이름"
          required
          value={name}
          error={error ?? undefined}
          placeholder="예: 7월 한정 라이브 경매"
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
          }}
        />

        {/* 방송 중에도 고칠 수 있으므로 잠금 안내 위에 둔다. 안내문이 "아래 설정"을 가리킨다. */}
        <div className="mt-5">
          <TextField
            label="라이브 방송 URL"
            type="url"
            value={liveUrl}
            placeholder="https://..."
            hint="비우고 저장하면 방송 링크가 지워집니다."
            onChange={(event) => setLiveUrl(event.target.value)}
          />
        </div>

        {locked && (
          <p className="mt-5 flex items-start gap-2 rounded-[14px] bg-fill px-4 py-3.5 text-[12px] leading-[1.6] font-medium text-neutral-tertiary">
            <Lock aria-hidden className="mt-0.5 size-3.5 shrink-0" />
            <span>
              아래 설정은 참여자가 이미 보고 입찰을 판단한 조건이라 경매가
              시작된 뒤에는 바꿀 수 없어요.
            </span>
          </p>
        )}

        {/*
          지우기는 넘기지 않는다(`onRemove` 없음). 수정 요청은 값이 없으면 기존
          값을 유지해서 "커버를 없앰"을 표현할 방법이 없다.
        */}
        <div className="mt-5">
          <ImageUploadField
            label="대표 이미지"
            uploadText="커버 이미지 변경"
            maxWidth={240}
            initialUrl={room.coverImageUrl}
            disabled={locked}
            onFileChange={setCoverFile}
          />
        </div>

        <div className="mt-5">
          <TextAreaField
            label="소개"
            value={description}
            disabled={locked}
            placeholder="경매방을 간단히 소개해 주세요."
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>

        <h3 className="mt-7 text-[14px] font-bold text-foreground">
          마감 연장
        </h3>
        <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
          종료 직전 입찰이 발생하면 마감 시간을 자동으로 연장합니다.
        </p>

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <NumberField
            label="마감 임박 기준"
            unit="분"
            steps={[1]}
            min={MIN_SOFT_CLOSE_MINUTES}
            max={MAX_SOFT_CLOSE_MINUTES}
            value={triggerMinutes}
            disabled={locked}
            onValueChange={setTriggerMinutes}
            error={softCloseMinutesError(triggerMinutes)}
          />
          <NumberField
            label="연장 시간"
            unit="분"
            steps={[1]}
            min={MIN_SOFT_CLOSE_MINUTES}
            max={MAX_SOFT_CLOSE_MINUTES}
            value={extendMinutes}
            disabled={locked}
            onValueChange={setExtendMinutes}
            error={softCloseMinutesError(extendMinutes)}
          />
        </div>

        {/*
          입찰 단위는 물품을 추가할 때 물품으로 복사된다. 방 값만 고치면 이미
          들어간 물품과 어긋나서 서버가 아예 수정 대상에서 뺐다.
        */}
        <p className="mt-5 rounded-[14px] bg-surface-subtle px-4 py-3.5 text-[12px] leading-[1.6] font-medium text-neutral-tertiary">
          입찰 단위는 {(room.bidIncrement ?? 0).toLocaleString('ko-KR')}
          원이에요. 물품이 이 값을 복사해 가지고 있어서 방을 만든 뒤에는 바꿀 수
          없어요.
        </p>

        <div className="mt-6 flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="h-12 flex-1 rounded-xl"
            disabled={saving}
            onClick={onClose}
          >
            취소
          </Button>
          <Button
            type="submit"
            variant="brand"
            size="lg"
            className="h-12 flex-1 rounded-xl"
            disabled={saving}
          >
            {saving ? '저장 중…' : '저장'}
          </Button>
        </div>
      </form>
    </>
  )
}

/** 서버는 초, 화면은 분이다. 0 분은 고를 수 없어 최소 1 분으로 올린다. */
function toMinutes(seconds: number | undefined): number {
  if (!seconds) return 1
  return Math.max(1, Math.round(seconds / 60))
}

/**
 * **바뀐 필드만** 담는다.
 *
 * PATCH 가 부분 병합이라 전부 보내도 결과는 같아 보이지만, 진행 중인 방에서는
 * 손대지 않은 필드 하나가 실려 있는 것만으로 서버가 409 로 거절한다
 * (이름 밖의 필드가 있으면 "시작 여부"를 따지기 때문). 안 건드린 값은 안 보낸다.
 */
function buildPatch({
  room,
  name,
  description,
  liveUrl,
  triggerMinutes,
  extendMinutes,
  locked,
}: {
  room: AuctionRoomPublicResponseDto
  name: string
  description: string
  liveUrl: string
  triggerMinutes: number
  extendMinutes: number
  locked: boolean
}): AuctionRoomUpdateRequestDto {
  const patch: AuctionRoomUpdateRequestDto = {}

  if (name.trim() !== (room.name ?? '')) patch.name = name.trim()

  /*
   * 방송 링크는 잠금 위에 있다 — 경매가 시작된 뒤에도 보낼 수 있다.
   *
   * 빈 문자열도 그대로 보낸다. 서버가 "지운다"는 뜻으로 받아 null 로 저장한다.
   * 예전에는 빈 값이 400 이라 여기서 걸렀고, 그래서 한 번 넣은 방송 링크를 지울
   * 수단이 아예 없었다(#162).
   */
  if (liveUrl.trim() !== (room.liveUrl ?? '')) {
    patch.liveUrl = liveUrl.trim()
  }

  if (locked) return patch

  if (description.trim() !== (room.description ?? '')) {
    patch.description = description.trim()
  }
  if (triggerMinutes !== toMinutes(room.softCloseTriggerSeconds)) {
    patch.softCloseTriggerSeconds = triggerMinutes * 60
  }
  if (extendMinutes !== toMinutes(room.softCloseExtendSeconds)) {
    patch.softCloseExtendSeconds = extendMinutes * 60
  }

  return patch
}
