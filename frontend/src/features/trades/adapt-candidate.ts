import type {
  DealCandidateListResponseDto,
  DealCandidateResponseDto,
  DealCandidateResponseDtoDealStatus,
} from '@/api/generated/model'

import type { DealRole } from './adapt-deal'

/**
 * 후보 한 명의 거래 상태. 서버가 (DB 상태, 순위)로 계산해 준다 — DB 의 `WAITING`
 * 중 지금 낙찰 권한을 가진 한 명만 `IN_PROGRESS` 다. 화면에서 다시 계산하지 않는다.
 */
export type CandidateStatus = DealCandidateResponseDtoDealStatus

export interface CandidateRow {
  dealCandidateId: number
  candidateRank: number
  nickname: string
  bidAmount: number
  dealStatus: CandidateStatus
  /**
   * 판매자가 볼 때 거래 상대인 후보(거래 중·성사)만 값이 있다. 나머지는 서버가
   * 응답에서 아예 빼므로, 연락처를 보여줄지는 이 값의 유무로만 판단한다.
   */
  phoneNumber: string | null
  isMe: boolean
}

export interface CandidatePage {
  /** 서버가 판정한 내 역할. 판매자도 후보도 아니면 403 이라 여기까지 오지 않는다. */
  viewerRole: DealRole | null
  /** 구매자의 순위. 요청한 페이지 밖에 있어도 값이 온다. 판매자는 없다. */
  myRank: number | null
  rows: CandidateRow[]
  page: number
  totalPages: number
  totalElements: number
}

/** 서버가 고정한 페이지 크기. 클라이언트가 정할 수 없다. */
export const CANDIDATE_PAGE_SIZE = 5

/** `GET /auction-items/{id}/deal-candidates` 응답을 화면이 쓸 모양으로 좁힌다. */
export function toCandidatePage(
  dto: DealCandidateListResponseDto | undefined,
): CandidatePage {
  const page = dto?.candidates

  return {
    viewerRole: dto?.viewerRole ?? null,
    myRank: dto?.myRank ?? null,
    rows: (page?.content ?? []).flatMap(toRow),
    page: page?.page ?? 0,
    totalPages: page?.totalPages ?? 0,
    totalElements: page?.totalElements ?? 0,
  }
}

/**
 * 순위·상태가 없는 후보는 표에 그릴 수 없어 버린다. DTO 가 전부 optional 인 건
 * springdoc 이 `required` 를 안 내려서일 뿐이고 서버는 항상 채워 준다.
 */
function toRow(dto: DealCandidateResponseDto): CandidateRow[] {
  if (
    dto.dealCandidateId == null ||
    dto.candidateRank == null ||
    dto.dealStatus == null
  ) {
    return []
  }

  return [
    {
      dealCandidateId: dto.dealCandidateId,
      candidateRank: dto.candidateRank,
      nickname: dto.nickname ?? '알 수 없음',
      bidAmount: dto.bidAmount ?? 0,
      dealStatus: dto.dealStatus,
      phoneNumber: dto.phoneNumber ?? null,
      isMe: dto.isMe ?? false,
    },
  ]
}
