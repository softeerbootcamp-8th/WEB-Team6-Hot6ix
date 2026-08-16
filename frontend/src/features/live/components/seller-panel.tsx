import { ExternalLink, X } from 'lucide-react'

import { ProfilePhoto } from '@/components/profile-photo'
import { formatUrlLabel, toHref } from '@/lib/format'
import type { AuctionRoomDetail } from '@/types/domain'

/**
 * 판매자 정보 패널 (#192 · #193).
 *
 * 방 헤더의 판매자 이름을 누르면 오른쪽 열 자리에 들어온다. 모바일에서는 같은
 * 컴포넌트가 다이얼로그 안에 들어간다.
 *
 * **방송 링크가 여기 있는 이유는 자리가 없어서가 아니다.** UpBid 는 영상을 직접
 * 내보내지 않고 판매자의 SNS 방송에 입찰만 얹는 서비스라, 방송으로 건너가는 것과
 * "누가 파는지"가 사실 같은 질문이다. 헤더에 버튼을 하나 더 다는 것보다 판매자를
 * 눌러 들어오는 편이 그 관계를 그대로 보여준다.
 *
 * 방 상태로 링크를 가리지 않는다. 종료된 방의 방송이 살아 있는지는 SNS 쪽 사정이라
 * 화면이 미리 판단할 근거가 없다.
 */
export function SellerPanel({
  room,
  onClose,
}: {
  room: AuctionRoomDetail
  onClose: () => void
}) {
  return (
    <div className="flex h-full flex-col rounded-[20px] bg-card p-4">
      <div className="flex shrink-0 items-start gap-2">
        <div className="min-w-0">
          <h2 className="text-[17px] font-bold text-foreground">판매자 정보</h2>
          <p className="mt-1 truncate text-[12px] font-medium text-neutral-tertiary">
            {room.title}
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          aria-label="판매자 정보 닫기"
          className="ease-soft ml-auto flex size-8 shrink-0 items-center justify-center rounded-full text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      <div className="mt-5 flex min-h-0 flex-1 flex-col items-center overflow-y-auto text-center">
        <ProfilePhoto
          src={room.sellerImageUrl}
          iconClassName="size-10"
          className="size-24 shrink-0 rounded-full border bg-brand-50"
        />

        <p className="mt-4 text-[18px] font-bold text-foreground">
          {room.sellerName || '판매자'}
        </p>

        {room.description && (
          <p className="mt-2 text-[13px] font-medium whitespace-pre-line text-neutral-tertiary">
            {room.description}
          </p>
        )}
      </div>

      {/*
       * 링크가 없는 방이 많다. 없으면 자리를 아예 차지하지 않는다 — 눌러도
       * 아무 일이 없는 버튼을 두면 방송이 없는 방인지 링크가 깨진 건지 모른다.
       */}
      {room.liveUrl ? (
        <div className="mt-4 shrink-0">
          <a
            href={toHref(room.liveUrl)}
            target="_blank"
            rel="noopener noreferrer"
            className="ease-soft flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
          >
            <ExternalLink aria-hidden className="size-4" />
            방송 보러 가기
          </a>
          <p className="mt-2 truncate text-center text-[11px] font-medium text-neutral-muted">
            {formatUrlLabel(room.liveUrl)}
          </p>
        </div>
      ) : (
        <p className="mt-4 shrink-0 text-center text-[12px] font-medium text-neutral-muted">
          판매자가 등록한 방송 링크가 없어요.
        </p>
      )}
    </div>
  )
}
