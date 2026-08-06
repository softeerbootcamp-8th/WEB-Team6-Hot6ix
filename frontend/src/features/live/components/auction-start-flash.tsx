import { AuctionStartAnimation } from '@/components/AuctionStartAnimation'

/**
 * 물품 경매가 시작된 직후 그 물품 카드를 덮는 연출.
 *
 * 마감·연장과 같은 자리에 같은 규칙으로 뜬다. 시작한 물품이 어느 것인지가
 * 시작했다는 사실만큼 중요해서, 화면 가운데에 띄우면 목록에서 그 카드를
 * 다시 찾아야 한다.
 *
 * 마감과 달리 어둡게 덮지 않는다. 이제부터 입찰할 수 있는 물품이라
 * 아래의 현재가와 남은 시간이 계속 읽혀야 한다.
 *
 * 문구는 APNG 가 아니라 컴포넌트가 DOM 으로 그린다(README). 카드가 가로로
 * 길고 낮아서(316×130) 기본 라벨 대신 옆에 붙여 그린다.
 */
export function AuctionStartFlashOverlay({
  flash,
}: {
  flash: AuctionStartFlashState
}) {
  return (
    <span className="animate-start-stamp absolute inset-0 z-10 flex items-center justify-center gap-2 rounded-2xl px-3">
      <span className="aspect-square h-full max-h-[132px] shrink-0">
        {/*
         * 컴포넌트가 스스로 `role="status"` 로 알린다. 덮개째로 `aria-hidden`
         * 하면 그 알림까지 사라져서, 직접 그린 문구만 감춘다.
         */}
        <AuctionStartAnimation
          replayKey={`${flash.itemId}-${flash.startedAt}`}
          size="100%"
          showLabel={false}
        />
      </span>

      <span aria-hidden className="flex min-w-0 flex-col">
        <span className="text-[20px] font-extrabold tracking-tight text-brand-500">
          경매 시작
        </span>
        <span className="mt-0.5 text-[11px] font-semibold text-neutral-tertiary">
          지금부터 입찰할 수 있어요
        </span>
      </span>
    </span>
  )
}

export interface AuctionStartFlashState {
  itemId: number
  /** 같은 물품을 다시 시작할 일은 없지만, 재생을 확실히 갈라 주려고 같이 쓴다. */
  startedAt: string
}

/**
 * 연출을 띄워두는 시간.
 *
 * APNG 가 1.5초짜리다. 컴포넌트가 스스로 사라지지 않으므로(README) 띄운 쪽에서
 * 지워야 한다. 끝나자마자 걷으면 뚝 끊겨 보여서 조금 여유를 둔다.
 * `animate-start-stamp` 지속시간과 같아야 연출이 중간에 잘리지 않는다.
 */
export const AUCTION_START_FLASH_MS = 1700
