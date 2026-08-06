import { cn } from '@/lib/utils'
import type { RoomEvent } from '@/mocks/types'

/**
 * 이벤트 줄 위에 붙는 물품 이름표.
 *
 * 시작·종료 이벤트는 문구 자체가 `{물품명} 경매가 시작됐어요` 라 물품이
 * 드러나는데, 입찰과 연장은 `{닉네임}님이 {금액} 입찰` 이라 물품명이
 * `subtitle` 에만 있었다. 그 자리는 흐린 12px 보조 줄이라 물품이 여러 개인
 * 방에서는 어느 물품 이야기인지 사실상 안 보였다. 그 둘만 이름표로 올린다.
 *
 * 붙일지 말지를 여기서 정하고 아래 여백까지 들고 있다. 부르는 쪽이 판정
 * 함수를 따로 쓰면 피드 두 곳(웹·모바일)에서 조건이 갈라진다.
 */
export function EventItemTag({ event }: { event: RoomEvent }) {
  if (!isTagged(event)) return null

  return (
    <span className="mb-1 flex w-fit max-w-full items-center rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-bold text-brand-600">
      <span className="truncate">{event.subtitle}</span>
    </span>
  )
}

/**
 * 이름표로 올라가지 않은 `subtitle` 을 문구 아래 보조 줄로 적는다.
 * (낙찰 확정의 `120,000원 · 홍길동님` 같은 것)
 *
 * 이름표와 짝이라 같은 파일에 둔다 — 둘이 갈라지면 물품명이 이름표와
 * 보조 줄에 두 번 찍힌다.
 */
export function EventSubtitleLine({ event }: { event: RoomEvent }) {
  if (isTagged(event) || !event.subtitle) return null

  return (
    <p
      className={cn(
        'mt-1 text-[12px]',
        event.kind === 'CLOSE'
          ? 'font-semibold text-success'
          : 'font-normal text-neutral-tertiary',
      )}
    >
      {event.subtitle}
    </p>
  )
}

/** 물품명이 문구에 없어서 이름표가 필요한 이벤트인지. */
function isTagged(event: RoomEvent): boolean {
  const kindNeedsTag = event.kind === 'BID' || event.kind === 'EXTEND'
  return kindNeedsTag && Boolean(event.subtitle)
}
