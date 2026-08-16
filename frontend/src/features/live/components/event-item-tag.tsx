import type { RoomEvent } from '@/types/domain'

/**
 * 이벤트 줄 위에 붙는 물품 이름표.
 *
 * 방 피드에는 여러 물품의 이벤트가 섞여 흐른다. 예전에는 시작·마감·종료만
 * 문구 안에 물품명을 넣고 입찰·연장은 안 넣어서, 어떤 줄은 대상을 알 수 있고
 * 어떤 줄은 알 수 없었다. 지금은 종류와 상관없이 이 이름표 하나가 맡는다.
 */
export function EventItemTag({ event }: { event: RoomEvent }) {
  if (!event.itemName) return null

  return (
    <span className="mb-1 flex w-fit max-w-full items-center rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-semibold text-brand-600">
      <span className="truncate">{event.itemName}</span>
    </span>
  )
}
