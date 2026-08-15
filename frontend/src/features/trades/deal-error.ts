import { isAxiosError } from 'axios'

import { readValidationMessage } from '@/lib/validation-message'

/**
 * 거래 성사·실패 처리가 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다. 서버 문구는 개발자 기준이라 "그래서
 * 뭘 하면 되는지"가 없다 (`API-INTEGRATION.md` 3장). `bid-error.ts` 와 같은 모양이다.
 */

export interface DealErrorMessage {
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/**
 * 서버 `ErrorType` 의 code. 거래는 6000 번대(`DealErrorType`)지만 물품을 못
 * 찾을 때는 `AuctionErrorType`(4001)을 그대로 재사용한다.
 *
 * 6002·6004·6005·6006 은 모두 "내가 보던 화면이 이미 낡았다"는 뜻이라, 다시
 * 불러오면 무엇이 바뀌었는지 보인다고 알려준다.
 */
const BY_CODE: Record<number, DealErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  4001: {
    title: '없는 물품이에요',
    description: '삭제되었을 수 있어요. 거래 내역에서 다시 들어와 주세요.',
  },
  6001: {
    title: '이 거래를 처리할 수 없어요',
    description: '물품을 등록한 판매자만 거래 상태를 바꿀 수 있어요.',
  },
  6002: {
    title: '아직 낙찰이 확정되지 않았어요',
    description: '경매가 마감되면 거래를 처리할 수 있어요.',
  },
  6003: {
    title: '낙찰 후보를 찾을 수 없어요',
    description: '목록에서 빠졌을 수 있어요. 새로고침해 주세요.',
  },
  6004: {
    title: '이미 처리된 후보예요',
    description: '최신 상태를 불러왔어요. 다시 확인해 주세요.',
  },
  6005: {
    title: '지금 차례인 후보가 아니에요',
    description: '순위가 바뀌었어요. 최신 목록에서 다시 처리해 주세요.',
  },
  6006: {
    title: '이미 완료된 거래예요',
    description: '더 처리할 후보가 없어요.',
  },
}

const UNKNOWN: DealErrorMessage = {
  title: '거래를 처리하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toDealErrorMessage(error: unknown): DealErrorMessage {
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
    console.error('처리하지 않은 거래 에러', error.response.status, code, error)
  }

  // 어느 칸이 왜 걸렸는지는 서버가 알려준 문구가 표보다 정확하다.
  const detail = readValidationMessage(error)
  const message = known ?? UNKNOWN

  return detail === null ? message : { ...message, description: detail }
}
