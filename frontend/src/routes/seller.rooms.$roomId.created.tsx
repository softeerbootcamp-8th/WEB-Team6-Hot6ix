import { createFileRoute, Link } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { QrCode } from '@/components/qr-code'
import { useGetShareInfo } from '@/api/generated/경매방/경매방'
import { requireMember } from '@/lib/route-guards'
import { toast } from '@/lib/toast'

/**
 * 경매방 생성 완료 (Figma `WEB-10 · 판매자 · 경매방 생성 완료`, 713:4131).
 *
 * 640×540 카드 한 장. 제목·설명 아래 참여 링크 줄(440 링크 + 16 간격 +
 * 120 복사 버튼), 그 아래 160 QR, 맨 아래 576 CTA다.
 */
export const Route = createFileRoute('/seller/rooms/$roomId/created')({
  beforeLoad: requireMember,
  component: AuctionRoomCreatedPage,
})

function AuctionRoomCreatedPage() {
  const { roomId } = Route.useParams()

  // 공유 링크는 서버가 조립해서 준다(share_code + 프론트 베이스 URL).
  const { data, isError } = useGetShareInfo(Number(roomId))
  const shareUrl = data?.data?.shareUrl

  const copy = async () => {
    if (!shareUrl) return
    try {
      await navigator.clipboard.writeText(shareUrl)
      toast.success('참여 링크를 복사했어요', { description: shareUrl })
    } catch {
      // 클립보드 권한이 없으면 직접 복사할 수 있게 링크를 그대로 둔다.
      toast.error('링크를 복사하지 못했어요', {
        description: '주소를 길게 눌러 직접 복사해 주세요.',
      })
    }
  }

  return (
    <AppShell title="경매방 생성 완료" className="max-w-[1280px]">
      <section className="mx-auto w-full max-w-[640px] rounded-[24px] border bg-card px-8 pt-12 pb-[58px]">
        <h1 className="text-center text-[26px] font-extrabold text-foreground">
          경매방이 만들어졌어요
        </h1>
        <p className="mt-3 text-center text-[14px] font-medium text-neutral-tertiary">
          참여 링크를 공유하거나 경매방으로 바로 이동하세요.
        </p>

        {/* 참여 링크 — 440 + 16 + 120 */}
        <div className="mt-8 flex gap-4">
          <p className="flex h-[60px] min-w-0 flex-1 items-center truncate rounded-[20px] border bg-surface-subtle px-5 text-[14px] font-semibold text-foreground">
            {shareUrl ??
              (isError ? '링크를 불러오지 못했어요' : '불러오는 중…')}
          </p>
          <button
            type="button"
            onClick={copy}
            disabled={!shareUrl}
            className="ease-soft h-[60px] w-[120px] shrink-0 rounded-[14px] bg-brand-50 text-[13px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-200 active:scale-95 disabled:pointer-events-none disabled:opacity-40"
          >
            링크 복사
          </button>
        </div>

        {/* QR — 160×160 */}
        <div className="mt-6 flex justify-center">
          {/* Figma 기준 160×160 고정 */}
          <QrCode
            value={shareUrl}
            error={isError}
            className="size-40 rounded-[20px]"
          />
        </div>

        <Link
          to="/rooms/$roomId"
          params={{ roomId }}
          className="ease-soft mt-11 flex h-14 w-full items-center justify-center rounded-[14px] bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
        >
          경매방으로 이동
        </Link>
      </section>
    </AppShell>
  )
}
