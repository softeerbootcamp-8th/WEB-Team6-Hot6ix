import { Check, Copy, QrCode } from 'lucide-react'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import { requireMember } from '@/lib/route-guards'

export const Route = createFileRoute('/seller/rooms/$roomId/created')({
  beforeLoad: requireMember,
  component: AuctionRoomCreatedPage,
})

function AuctionRoomCreatedPage() {
  const { roomId } = Route.useParams()
  const [copied, setCopied] = useState(false)

  const shareUrl = `${window.location.origin}/join/${MOCK_ROOM_DETAIL.shareCode}`

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      // 클립보드 권한이 없으면 사용자가 직접 복사할 수 있게 둔다.
      setCopied(false)
    }
  }

  return (
    <AppShell title="경매방 생성 완료" className="max-w-[608px]">
      <section className="rounded-4xl border bg-card p-6 text-center md:p-10">
        <span
          aria-hidden
          className="mx-auto flex size-20 items-center justify-center rounded-full bg-success-surface"
        >
          <Check className="size-9 text-success" strokeWidth={3} />
        </span>

        <h1 className="mt-7 text-[22px] font-extrabold text-foreground md:text-[24px]">
          경매방이 만들어졌어요
        </h1>
        <p className="mt-3 text-body font-medium text-neutral-tertiary">
          아래 링크나 QR을 SNS에 공유하면 바로 참여할 수 있어요.
        </p>

        <div className="mt-8 rounded-3xl bg-surface-subtle p-5 text-left">
          <p className="text-caption font-semibold text-neutral-secondary">
            참여 링크
          </p>
          <div className="mt-2 flex gap-2">
            <input
              readOnly
              value={shareUrl}
              aria-label="참여 링크"
              className="h-12 min-w-0 flex-1 rounded-xl border border-input bg-card px-4 text-label font-medium text-neutral-secondary outline-none"
            />
            <Button
              type="button"
              variant="outline"
              onClick={copy}
              className="h-12 shrink-0 gap-1.5 rounded-xl px-4 text-label font-bold"
            >
              <Copy aria-hidden className="size-4" />
              {copied ? '복사됨' : '복사'}
            </Button>
          </div>
          <p
            aria-live="polite"
            className="mt-2 text-caption font-normal text-neutral-muted"
          >
            {copied
              ? '링크를 복사했어요.'
              : '링크만 있으면 누구나 둘러볼 수 있어요.'}
          </p>

          <div className="mt-6 flex flex-col items-center gap-3 rounded-2xl bg-card p-6">
            <span
              aria-hidden
              className="flex size-32 items-center justify-center rounded-2xl bg-fill text-neutral-muted"
            >
              <QrCode className="size-14" />
            </span>
            <p className="text-caption font-normal text-neutral-tertiary">
              QR 코드 · 오프라인에서 바로 공유
            </p>
          </div>
        </div>

        <Link
          to="/rooms/$roomId"
          params={{ roomId }}
          className="mt-8 block rounded-2xl bg-primary py-4 text-card-title font-bold text-primary-foreground transition-opacity hover:opacity-90"
        >
          경매방으로 이동
        </Link>
        <Link
          to="/rooms"
          className="mt-3 block text-label font-bold text-neutral-tertiary hover:text-neutral-secondary"
        >
          내 경매방 목록으로
        </Link>
      </section>
    </AppShell>
  )
}
