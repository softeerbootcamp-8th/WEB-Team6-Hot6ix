import { isAxiosError } from 'axios'

import { readValidationMessage } from '@/lib/validation-message'

/**
 * 경매방 생성·물품 추가가 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다 (`API-INTEGRATION.md` 3장).
 * 예외는 검증 실패의 `errors[]` 하나다 (`readValidationMessage`).
 * `product-error.ts` · `profile-error.ts` 와 같은 모양이다.
 */

export interface AuctionRoomErrorMessage {
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 경매방·물품은 4000 번대, 상품은 5000, 공통은 2000 번대다. */
const BY_CODE: Record<number, AuctionRoomErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  2002: {
    title: '입력값을 다시 확인해 주세요',
    description: '경매방 이름과 입찰 단위를 확인해 주세요.',
  },
  3002: {
    title: '판매자 프로필이 없어요',
    description: '판매자 정보에서 프로필을 먼저 등록해 주세요.',
  },
  4002: {
    title: '경매방을 찾을 수 없어요',
    description: '삭제되었거나 접근할 수 없는 경매방이에요.',
  },
  4003: {
    title: '경매가 시작돼 고칠 수 없어요',
    description: '경매방 이름 말고는 시작 전에만 바꿀 수 있어요.',
  },
  4004: {
    title: '종료된 경매방이에요',
    description: '종료된 경매방은 물품 추가도 설정 수정도 할 수 없어요.',
  },
  4005: {
    title: '이미 경매에 올라간 상품이에요',
    description: '다른 경매방에서 뺀 뒤에 다시 추가할 수 있어요.',
  },
  4009: {
    title: '물품을 더 담을 수 없어요',
    description: '한 경매방에는 최대 100개까지 추가할 수 있어요.',
  },
  5001: {
    title: '상품을 찾을 수 없어요',
    description: '삭제되었거나 접근할 수 없는 상품이에요.',
  },
}

const UNKNOWN: AuctionRoomErrorMessage = {
  title: '처리하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toAuctionRoomErrorMessage(
  error: unknown,
): AuctionRoomErrorMessage {
  if (!isAxiosError(error)) return UNKNOWN

  // 네트워크가 끊기면 응답 자체가 없다.
  if (!error.response) {
    return {
      title: '연결이 끊겼어요',
      description: '네트워크를 확인하고 다시 시도해 주세요.',
    }
  }

  const code = (error.response.data as { code?: number } | undefined)?.code
  const known = code === undefined ? undefined : BY_CODE[code]

  if (import.meta.env.DEV && !known) {
    console.error(
      '처리하지 않은 경매방 에러',
      error.response.status,
      code,
      error,
    )
  }

  // 어느 칸이 왜 걸렸는지는 서버가 알려준 문구가 표보다 정확하다.
  const detail = readValidationMessage(error)
  const message = known ?? UNKNOWN

  return detail === null ? message : { ...message, description: detail }
}

/**
 * 벌크 추가 응답의 `failed` 항목을 한 줄 문구로 바꾼다. 서버가 단건 추가와 같은 code 를
 * 담아 주므로 위 표를 그대로 쓴다.
 */
export function toFailureReason(code: number | undefined): string {
  return (
    (code !== undefined ? BY_CODE[code] : undefined)?.title ??
    '추가하지 못했어요'
  )
}
