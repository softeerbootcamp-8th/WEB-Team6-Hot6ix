import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useEffect, useRef, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { GuestShell } from '@/components/layout/page-shell'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatPhoneNumber } from '@/lib/format'
import { cn } from '@/lib/utils'

export const Route = createFileRoute('/signup/phone')({
  validateSearch: (search: Record<string, unknown>): { redirect?: string } =>
    typeof search.redirect === 'string' ? { redirect: search.redirect } : {},
  component: PhoneVerificationPage,
})

/** 인증번호 유효 시간. 서버가 내려주는 값으로 대체될 자리다. */
const CODE_TTL_SECONDS = 180
const CODE_LENGTH = 6
/** 서버 연동 전까지 쓰는 임시 인증번호. 어떤 번호를 넣어도 통과한다. */
const MOCK_CODE = '000000'

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

function PhoneVerificationPage() {
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()

  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [sent, setSent] = useState(false)
  const [remaining, setRemaining] = useState(0)
  const codeInputRef = useRef<HTMLInputElement>(null)

  // 남은 시간 카운트다운. 화면을 떠나면 반드시 정리한다.
  useEffect(() => {
    if (!sent || remaining <= 0) return

    const timer = window.setInterval(() => {
      setRemaining((prev) => (prev <= 1 ? 0 : prev - 1))
    }, 1000)

    return () => window.clearInterval(timer)
  }, [sent, remaining])

  const phoneDigits = phone.replace(/\D/g, '')
  const canRequest = phoneDigits.length >= 10
  const expired = sent && remaining === 0
  // 목업 단계에서는 자릿수만 채우면 통과시킨다. 실제 확인은 서버가 한다.
  const canSubmit = sent && !expired && code.length > 0

  const requestCode = () => {
    if (!canRequest) return
    // TODO: POST /api/v1/phone-verifications 연동 (현재 목업)
    setSent(true)
    setRemaining(CODE_TTL_SECONDS)
    // 아직 실제 문자가 오지 않으니 화면을 막지 않도록 미리 채워둔다.
    setCode(MOCK_CODE)
    codeInputRef.current?.focus()
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) return
    // TODO: POST /api/v1/phone-verifications/{id}/confirm 연동 (현재 목업)
    void navigate({
      to: '/signup/complete',
      search: redirect ? { redirect } : {},
    })
  }

  return (
    <GuestShell
      state="전화번호 인증"
      title="전화번호 인증"
      // 카드 하나뿐이라 위쪽에만 붙어 있었다. 남는 높이를 나눠 가운데로 모은다.
      className="flex min-h-[calc(100svh-8rem)] max-w-[608px] flex-col justify-center px-5 py-6 md:min-h-[calc(100svh-10rem)] md:py-10"
    >
      <section className="rounded-4xl border bg-card p-6 md:p-8">
        <span className="inline-flex h-7 items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 px-3 text-[11px] font-extrabold tracking-[0.08em] text-brand-600">
          <span aria-hidden className="size-1.5 rounded-full bg-brand-500" />
          STEP 2 · 본인 확인
        </span>

        <h1 className="mt-4 text-[22px] font-extrabold text-foreground md:text-[24px]">
          마지막으로 본인만 확인할게요
        </h1>
        <p className="mt-2 text-[13px] leading-[1.6] font-medium text-neutral-tertiary">
          낙찰 후 판매자와 연락하려면 번호가 필요해요. 인증한 번호는 거래
          상대에게만 보입니다.
        </p>

        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-6">
          <div className="flex flex-col gap-2">
            <Label
              htmlFor="phone"
              className="text-caption font-semibold text-neutral-secondary"
            >
              전화번호
            </Label>
            <div className="flex gap-2">
              <Input
                id="phone"
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                placeholder="010-1234-5678"
                maxLength={13}
                value={phone}
                onChange={(event) =>
                  setPhone(formatPhoneNumber(event.target.value))
                }
                disabled={sent && !expired}
              />
              <Button
                type="button"
                variant="secondary"
                onClick={requestCode}
                disabled={!canRequest || (sent && !expired)}
                className="h-12 shrink-0 rounded-xl px-4 text-caption font-bold"
              >
                {sent ? '인증번호 다시 받기' : '인증번호 받기'}
              </Button>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <Label
              htmlFor="code"
              className="text-caption font-semibold text-neutral-secondary"
            >
              인증번호
            </Label>
            <div className="relative">
              <Input
                id="code"
                ref={codeInputRef}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={CODE_LENGTH}
                placeholder={`${CODE_LENGTH}자리 인증번호 입력`}
                value={code}
                onChange={(event) =>
                  setCode(event.target.value.replace(/\D/g, ''))
                }
                disabled={!sent || expired}
                aria-invalid={expired}
                aria-describedby="code-help"
                className="pr-16"
              />
              {sent && (
                <span
                  aria-live="polite"
                  className={cn(
                    'absolute top-1/2 right-4 -translate-y-1/2 text-label font-bold',
                    expired ? 'text-live' : 'text-brand-500',
                  )}
                >
                  {formatCountdown(remaining)}
                </span>
              )}
            </div>

            <p
              id="code-help"
              className={cn(
                'text-caption font-normal',
                expired ? 'text-live' : 'text-neutral-tertiary',
              )}
            >
              {expired
                ? '인증 시간이 지났어요. 인증번호를 다시 받아주세요.'
                : sent
                  ? '연동 전이라 어떤 번호를 넣어도 통과합니다.'
                  : '가입에는 본인 확인이 한 번 필요해요.'}
            </p>
          </div>

          <Button
            type="submit"
            size="cta"
            variant="brand"
            disabled={!canSubmit}
          >
            인증하고 가입 완료
          </Button>
        </form>

        <div className="mt-8 rounded-3xl border bg-surface-subtle p-5">
          <h2 className="text-caption font-bold text-neutral-secondary">
            개인정보 수집·이용 안내
          </h2>
          <dl className="mt-3 space-y-1.5 text-badge font-medium text-neutral-tertiary">
            <div className="flex gap-2">
              <dt className="shrink-0 text-neutral-muted">수집 항목</dt>
              <dd>휴대전화번호</dd>
            </div>
            <div className="flex gap-2">
              <dt className="shrink-0 text-neutral-muted">이용 목적</dt>
              <dd>본인 확인 및 거래 연락</dd>
            </div>
            <div className="flex gap-2">
              <dt className="shrink-0 text-neutral-muted">보유 기간</dt>
              <dd>회원 탈퇴 시까지 (관계 법령에 따라 보관되는 경우 제외)</dd>
            </div>
          </dl>
          <Link
            to="/terms/privacy"
            className="mt-4 inline-block text-badge font-extrabold text-brand-500 hover:underline"
          >
            개인정보처리방침 보기 →
          </Link>
        </div>
      </section>
    </GuestShell>
  )
}
