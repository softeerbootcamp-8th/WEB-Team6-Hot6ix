import { AuctionCloseAnimation } from '@/components/AuctionCloseAnimation'
import type { AuctionItemDetail } from '@/types/domain'

/**
 * 마감된 직후 카드를 덮는 연출. 물품 카드와 리더보드 카드가 같은 것을 쓴다.
 *
 * **낙찰과 유찰의 구성이 다르다.**
 *
 * 낙찰은 2.83초짜리 APNG 가 마지막에 도장까지 찍어 주므로 그림만 크게 둔다.
 * 유찰은 움직이는 그림이 없다(6.1MB 라 넣지 않기로 했다 —
 * `AuctionCloseAnimation` 주석). 정지 이미지 하나만 3초 띄우면 화면이 멎은
 * 것처럼 보여서, **그림은 도장처럼 찍히게 하고 "유찰" 표시를 DOM 으로
 * 그린다.** 이미지를 새로 받지 않으므로 저장소와 전송량이 늘지 않는다.
 */
export function AuctionCloseFlashOverlay({
  item,
}: {
  item: AuctionItemDetail
}) {
  return (
    <span className="animate-closed-stamp absolute inset-0 z-10 flex items-center justify-center gap-2 rounded-2xl">
      {item.sold ? (
        /*
         * 정사각 APNG 라서 카드 높이에 맞춘 정사각 칸을 만들어 넣는다.
         * 폭에 맞추면 낮은 카드에서 위아래가 잘린다.
         */
        <span className="aspect-square h-full">
          <AuctionCloseAnimation
            outcome="sold"
            replayKey={item.id}
            size="100%"
          />
        </span>
      ) : (
        <>
          <span className="animate-unsold-in aspect-square h-full max-h-[132px] shrink-0">
            <AuctionCloseAnimation
              outcome="closed"
              replayKey={item.id}
              size="100%"
            />
          </span>

          {/* 그림이 못 하는 말을 대신한다. 낙찰 도장과 같은 각도로 찍힌다. */}
          <span aria-hidden className="flex min-w-0 flex-col">
            <span className="animate-closed-label origin-left text-[20px] font-extrabold tracking-tight text-white">
              유찰
            </span>
            <span className="mt-0.5 text-[11px] font-semibold text-white/70">
              입찰 없이 마감됐어요
            </span>
          </span>
        </>
      )}
    </span>
  )
}
