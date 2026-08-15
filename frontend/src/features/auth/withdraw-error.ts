import { isAxiosError } from 'axios'

import { readValidationMessage } from '@/lib/validation-message'

/**
 * 회원탈퇴가 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다 (`API-INTEGRATION.md` 3장).
 * `phone-verification-error.ts` · `deal-error.ts` 와 같은 모양이다.
 */

export interface WithdrawErrorMessage {
  title: string
  /** 아래 줄에 붙는 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 회원은 9000 번대, 판매자 프로필은 3000 번대다. */
const BY_CODE: Record<number, WithdrawErrorMessage> = {
  3003: {
    title: '진행 중인 경매방이 있어요',
    description: '방송 중인 경매방을 먼저 종료한 뒤 다시 시도해 주세요.',
  },
  9001: {
    title: '이미 탈퇴한 계정이에요',
    description: '다시 로그인해 주세요.',
  },
}

const UNKNOWN: WithdrawErrorMessage = {
  title: '탈퇴를 처리하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toWithdrawErrorMessage(error: unknown): WithdrawErrorMessage {
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
      '처리하지 않은 회원탈퇴 에러',
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
