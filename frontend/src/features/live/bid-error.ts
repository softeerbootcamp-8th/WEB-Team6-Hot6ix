import { isAxiosError } from 'axios'

/**
 * 입찰 실패 응답을 사용자에게 보여줄 문구로 바꾼다.
 *
 * 서버가 보낸 `message` 를 그대로 띄우지 않는다. 서버 문구는 개발자 기준이라
 * "그래서 뭘 하면 되는지"가 없다 (`API-INTEGRATION.md` 3장).
 */

export interface BidErrorMessage {
  /** 토스트 제목 */
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
  /** 같은 금액으로 다시 눌러볼 만한 실패인지. false 면 재시도를 권하지 않는다. */
  retryable: boolean
}

/**
 * 서버 `ErrorType` 의 code.
 *
 * 입찰은 7000 번대(`BidErrorType`)지만, 물품을 못 찾거나 회원이 없을 때는
 * `AuctionErrorType`·`CommonErrorType` 의 코드를 그대로 재사용한다.
 */
const BY_CODE: Record<number, BidErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '카카오로 로그인한 뒤 다시 입찰해 주세요.',
    retryable: false,
  },
  2002: {
    title: '입찰 금액을 확인해 주세요',
    description: '0보다 큰 금액을 입력해 주세요.',
    retryable: true,
  },
  2003: {
    title: '회원 정보를 찾을 수 없어요',
    description: '다시 로그인한 뒤 시도해 주세요.',
    retryable: false,
  },
  4001: {
    title: '없는 물품이에요',
    description: '목록에서 빠졌을 수 있어요. 새로고침해 주세요.',
    retryable: false,
  },
  7001: {
    title: '아직 입찰할 수 없어요',
    description: '경매가 시작되면 입찰이 열려요.',
    retryable: false,
  },
  7002: {
    title: '마감된 물품이에요',
    description: '다른 물품을 확인해 주세요.',
    retryable: false,
  },
  7003: {
    title: '이미 최고 입찰자예요',
    description: '남이 더 높게 부른 뒤에 다시 올릴 수 있어요.',
    retryable: false,
  },
  7004: {
    title: '입찰 금액이 부족해요',
    description: '최신 현재가 위로 다시 입찰해 주세요.',
    retryable: true,
  },
  7005: {
    title: '입찰 단위에 맞지 않아요',
    description: '입찰 단위만큼 올린 금액으로 입찰해 주세요.',
    retryable: true,
  },
  7006: {
    title: '거의 동시에 다른 입찰이 들어왔어요',
    description: '한 번 더 눌러주시면 처리돼요.',
    retryable: true,
  },
  7007: {
    title: '판매자는 입찰할 수 없어요',
    description: '본인이 올린 물품이에요.',
    retryable: false,
  },
  7008: {
    title: '경매방에 입장해야 입찰할 수 있어요',
    description: '공유받은 링크로 입장해 약관에 동의해 주세요.',
    retryable: false,
  },
}

const UNKNOWN: BidErrorMessage = {
  title: '입찰하지 못했어요',
  description: '잠시 뒤에 다시 시도해 주세요.',
  retryable: true,
}

export function toBidErrorMessage(error: unknown): BidErrorMessage {
  if (!isAxiosError(error)) return UNKNOWN

  // 네트워크가 끊기면 응답 자체가 없다.
  if (!error.response) {
    return {
      title: '연결이 끊겼어요',
      description: '네트워크를 확인하고 다시 시도해 주세요.',
      retryable: true,
    }
  }

  const code = (error.response.data as { code?: number } | undefined)?.code
  const known = code === undefined ? undefined : BY_CODE[code]

  if (import.meta.env.DEV && !known) {
    console.error('처리하지 않은 입찰 에러', error.response.status, code, error)
  }

  return known ?? UNKNOWN
}
