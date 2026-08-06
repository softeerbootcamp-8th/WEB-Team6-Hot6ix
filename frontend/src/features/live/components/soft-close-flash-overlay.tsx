import { SoftCloseAnimation } from '@/components/SoftCloseAnimation'
import {
  formatExtension,
  toExtensionMinutes,
  type SoftCloseFlash,
} from '@/features/live/soft-close-flash'

/**
 * 소프트클로즈로 마감이 밀린 직후 카드를 덮는 연출.
 *
 * 물품 카드와 리더보드 카드가 같은 것을 쓴다. 마감 도장과 같은 자리지만
 * 덮개를 어둡게 하지 않는다 — 경매가 계속되는 중이라 아래의 남은 시간과
 * 현재가가 계속 읽혀야 한다.
 *
 * **문구를 컴포넌트 기본 라벨 대신 직접 그린다.** `SoftCloseAnimation` 의
 * `extensionMinutes` 는 분만 받는데 이 서비스의 연장 폭은 30초라서, 기본
 * 라벨을 켜면 "+0분"이 된다. README 가 말하는 "이미지 아래 텍스트로 실제
 * 연장 시간 표시"는 지키되 단위만 맞춘 것이다.
 *
 * 카드가 가로로 길고 낮아서(316×130) 위아래로 쌓지 않고 옆에 붙인다.
 */
export function SoftCloseFlashOverlay({ flash }: { flash: SoftCloseFlash }) {
  return (
    <span
      aria-hidden
      className="animate-extend-stamp absolute inset-0 z-10 flex items-center justify-center gap-2 rounded-2xl px-3"
    >
      <span className="aspect-square h-full max-h-[132px] shrink-0">
        <SoftCloseAnimation
          extensionMinutes={toExtensionMinutes(flash.seconds)}
          replayKey={`${flash.itemId}-${flash.count}`}
          size="100%"
          showLabel={false}
        />
      </span>

      <span className="flex min-w-0 flex-col">
        <span className="flex items-baseline gap-1">
          <span className="text-[20px] font-extrabold tracking-tight text-notice">
            +{formatExtension(flash.seconds)}
          </span>
          <span className="text-[15px] font-extrabold text-foreground">
            연장
          </span>
        </span>
        <span className="mt-0.5 text-[11px] font-semibold text-neutral-tertiary">
          새 마감 시간이 적용됐어요
        </span>
      </span>
    </span>
  )
}
