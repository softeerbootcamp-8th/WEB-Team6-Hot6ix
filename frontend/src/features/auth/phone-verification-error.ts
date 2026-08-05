import { isAxiosError } from 'axios'

/**
 * 전화번호 인증·회원가입이 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다 (`API-INTEGRATION.md` 3장).
 * `profile-error.ts` · `deal-error.ts` 와 같은 모양이다.
 */

export interface PhoneVerificationErrorMessage {
  title: string
  /** 아래 줄에 붙는 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 인증·로그인은 1000 번대, SMS 인증은 8000 번대다. */
const BY_CODE: Record<number, PhoneVerificationErrorMessage> = {
  1009: {
    title: '가입 진행 정보가 만료됐어요',
    description: '처음부터 다시 카카오 로그인을 진행해 주세요.',
  },
  1010: {
    title: '전화번호 인증이 필요해요',
    description: '인증번호를 먼저 확인해 주세요.',
  },
  8001: {
    title: '인증번호를 보내지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
  8002: {
    title: '인증번호를 먼저 받아주세요',
    description: '인증번호 받기를 눌러주세요.',
  },
  8003: {
    title: '인증 시간이 지났어요',
    description: '인증번호를 다시 받아주세요.',
  },
  8004: {
    title: '인증번호가 일치하지 않아요',
    description: '문자로 받은 6자리 숫자를 다시 확인해 주세요.',
  },
  8005: {
    title: '인증 시도 횟수를 초과했어요',
    description: '인증번호를 다시 받아주세요.',
  },
  8006: {
    title: '잠시 후 다시 시도해 주세요',
    description: '인증번호 재발송은 1분 뒤부터 가능해요.',
  },
  8007: {
    title: '발송 횟수를 초과했어요',
    description: '1시간 뒤에 다시 시도해 주세요.',
  },
  8008: {
    title: '오늘 발송 횟수를 초과했어요',
    description: '내일 다시 시도해 주세요.',
  },
  8009: {
    title: '지금은 인증번호를 보낼 수 없어요',
    description: '서비스 전체 발송 한도를 넘었어요. 내일 다시 시도해 주세요.',
  },
}

const UNKNOWN: PhoneVerificationErrorMessage = {
  title: '처리하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toPhoneVerificationErrorMessage(
  error: unknown,
): PhoneVerificationErrorMessage {
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
      '처리하지 않은 전화번호 인증 에러',
      error.response.status,
      code,
      error,
    )
  }

  return known ?? UNKNOWN
}
