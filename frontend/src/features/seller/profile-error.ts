import { isAxiosError } from 'axios'

/**
 * 판매자 프로필 등록·수정이 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다 (`API-INTEGRATION.md` 3장).
 * `deal-error.ts` · `bid-error.ts` 와 같은 모양이다.
 */

export interface ProfileErrorMessage {
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 프로필은 3000 번대, 공통은 2000 번대다. */
const BY_CODE: Record<number, ProfileErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  2002: {
    title: '입력값을 다시 확인해 주세요',
    description: '가게 이름은 2~30자, 한 줄 소개는 100자까지예요.',
  },
  2003: {
    title: '회원 정보를 찾을 수 없어요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  3001: {
    title: '이미 등록한 프로필이 있어요',
    description: '판매자 정보에서 수정할 수 있어요.',
  },
  3002: {
    title: '등록된 프로필이 없어요',
    description: '먼저 판매자 프로필을 등록해 주세요.',
  },
  10002: {
    title: '이미지를 다시 올려주세요',
    description: '업로드가 끝나기 전에 저장했거나 주소가 만료됐어요.',
  },
}

const UNKNOWN: ProfileErrorMessage = {
  title: '저장하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toProfileErrorMessage(error: unknown): ProfileErrorMessage {
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
      '처리하지 않은 프로필 에러',
      error.response.status,
      code,
      error,
    )
  }

  return known ?? UNKNOWN
}
