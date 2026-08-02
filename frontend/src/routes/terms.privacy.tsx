import { createFileRoute } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'
import { LegalDocument } from '@/features/legal/components/legal-document'

export const Route = createFileRoute('/terms/privacy')({
  component: PrivacyPolicyPage,
})

const SECTIONS = [
  {
    title: '수집하는 개인정보',
    body: '카카오 로그인으로 제공받는 계정 식별자와 닉네임, 본인 확인을 위한 휴대전화번호를 수집합니다. 경매 참여 과정에서 입찰 이력과 접속 기록이 함께 저장됩니다.',
  },
  {
    title: '이용 목적',
    body: '본인 확인, 경매 참여와 낙찰 처리, 낙찰 이후 판매자·구매자 간 연락, 부정 이용 방지에 사용합니다.',
  },
  {
    title: '보유 기간',
    body: '회원 탈퇴 시까지 보관하며, 탈퇴 시 지체 없이 파기합니다. 다만 관계 법령에 따라 보관이 필요한 기록은 해당 기간 동안 별도로 보관합니다.',
  },
  {
    title: '제3자 제공',
    body: '낙찰이 확정된 경우에 한해 거래 당사자(판매자와 낙찰자)에게 연락처가 공개됩니다. 그 외에는 법령에 근거한 경우를 제외하고 제3자에게 제공하지 않습니다.',
  },
  {
    title: '이용자의 권리',
    body: '언제든지 마이페이지에서 등록한 정보를 확인·수정하거나 회원 탈퇴를 통해 개인정보 삭제를 요청할 수 있습니다.',
  },
]

function PrivacyPolicyPage() {
  return (
    <GuestShell title="개인정보처리방침" back className="max-w-[720px]">
      <LegalDocument
        title="개인정보처리방침"
        updatedAt="2026-07-01"
        sections={SECTIONS}
      />
    </GuestShell>
  )
}
