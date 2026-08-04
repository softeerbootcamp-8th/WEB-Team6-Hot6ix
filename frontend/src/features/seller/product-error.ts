import { isAxiosError } from 'axios'

/**
 * 상품 등록·수정·삭제가 거절된 이유를 사용자 문구로 바꾼다.
 *
 * 서버 `message` 를 그대로 띄우지 않는다 (`API-INTEGRATION.md` 3장).
 * `profile-error.ts` · `deal-error.ts` 와 같은 모양이다.
 */

export interface ProductErrorMessage {
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 상품은 5000 번대, 프로필은 3000, 공통은 2000 번대다. */
const BY_CODE: Record<number, ProductErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  2002: {
    title: '입력값을 다시 확인해 주세요',
    description: '상품명은 30자, 설명은 100자까지예요.',
  },
  3002: {
    title: '판매자 프로필이 없어요',
    description: '판매자 정보에서 프로필을 먼저 등록해 주세요.',
  },
  5001: {
    title: '상품을 찾을 수 없어요',
    description: '삭제되었거나 접근할 수 없는 상품이에요.',
  },
  5002: {
    title: '이미 경매에 올라간 상품이에요',
    description: '경매가 시작된 뒤로는 상품 정보를 고칠 수 없어요.',
  },
  5003: {
    title: '경매방에 담겨 있는 상품이에요',
    description: '경매방에서 물품을 먼저 뺀 뒤 삭제해 주세요.',
  },
  10002: {
    title: '이미지를 다시 올려주세요',
    description: '업로드가 끝나기 전에 저장했거나 주소가 만료됐어요.',
  },
}

const UNKNOWN: ProductErrorMessage = {
  title: '처리하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
}

export function toProductErrorMessage(error: unknown): ProductErrorMessage {
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
    console.error('처리하지 않은 상품 에러', error.response.status, code, error)
  }

  return known ?? UNKNOWN
}
