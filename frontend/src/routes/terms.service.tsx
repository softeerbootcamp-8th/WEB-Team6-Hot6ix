import { createFileRoute } from '@tanstack/react-router'

import {
  LegalDocument,
  LegalShell,
} from '@/features/legal/components/legal-document'

export const Route = createFileRoute('/terms/service')({
  component: TermsOfServicePage,
})

const SECTIONS = [
  {
    title: '서비스 소개',
    body: 'UpBid는 판매자가 SNS에서 진행하던 경매를 링크와 QR로 옮겨, 입찰과 낙찰을 서버 시간 기준으로 관리하도록 돕는 서비스입니다.',
  },
  {
    title: '입찰과 낙찰',
    body: '입찰은 서버에 기록된 시점을 기준으로 처리되며, 등록된 후에는 취소할 수 없습니다. 입찰 단위나 최소 금액에 미달하는 입찰은 자동으로 거절됩니다.',
  },
  {
    title: '마감과 자동 연장',
    body: '마감은 서버 시간을 기준으로 확정됩니다. 마감 직전에 입찰이 들어오면 판매자가 설정한 시간만큼 마감이 자동으로 미뤄집니다.',
  },
  {
    title: '낙찰 이후 거래',
    body: '낙찰 이후의 거래와 배송, 대금 정산은 판매자와 낙찰자가 직접 진행합니다. 1순위 낙찰자와 거래가 성사되지 않으면 차순위 입찰자에게 기회가 넘어갑니다.',
  },
  {
    title: '금지 행위',
    body: '거래 의사 없는 입찰, 타인 명의 도용, 서비스 운영을 방해하는 행위는 금지되며 이용이 제한될 수 있습니다.',
  },
]

function TermsOfServicePage() {
  return (
    <LegalShell title="이용약관">
      <LegalDocument
        title="이용약관"
        updatedAt="2026-07-01"
        sections={SECTIONS}
      />
    </LegalShell>
  )
}
