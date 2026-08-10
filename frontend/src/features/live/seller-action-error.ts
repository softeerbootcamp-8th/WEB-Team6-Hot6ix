import { readApiErrorCode } from '@/features/live/api-error'

/**
 * 판매자 조작(물품 추가·빼기·시작·마감 앞당기기) 실패 응답을 화면 문구로 바꾼다.
 *
 * 입찰(`bid-error.ts`)과 나눠 둔 이유는 코드 대역이 다르기 때문이다. 입찰은
 * 7000번대(`BidErrorType`)를 쓰고, 판매자 조작은 4000번대(`AuctionErrorType`)를 쓴다.
 *
 * 조작마다 나오는 코드가 겹치지 않아 표는 하나로 합쳤다 — 예를 들어 4008(동시 3개)은
 * 시작에서만, 4005(이미 다른 방에 올라간 상품)는 추가에서만 나온다. 어느 조작인지는
 * 표에 없는 코드를 만났을 때 쓸 마지막 문구를 고르는 데만 필요하다.
 */

/** 어떤 조작이었는지. 표에 없는 코드일 때의 문구가 이 값으로 갈린다. */
export type SellerAction =
  'add' | 'remove' | 'start' | 'closeRoom' | 'closeEarly'

export interface SellerActionErrorMessage {
  /** 토스트 제목 */
  title: string
  /** 아래 줄 설명. 다음에 뭘 하면 되는지 적는다. */
  description: string
}

/** 서버 `ErrorType` 의 code. 4000번대는 `AuctionErrorType`, 그 밖은 공용·판매자 프로필. */
const BY_CODE: Record<number, SellerActionErrorMessage> = {
  1005: {
    title: '로그인이 필요해요',
    description: '카카오로 로그인한 뒤 다시 시도해 주세요.',
  },
  2002: {
    title: '입력값을 확인해 주세요',
    description: '시작가와 진행 시간을 다시 확인해 주세요.',
  },
  2003: {
    title: '회원 정보를 찾을 수 없어요',
    description: '다시 로그인한 뒤 시도해 주세요.',
  },
  3002: {
    title: '판매자 프로필이 필요해요',
    description: '판매자 프로필을 먼저 등록해 주세요.',
  },
  4001: {
    title: '없는 물품이에요',
    description: '목록에서 빠졌을 수 있어요. 새로고침해 주세요.',
  },
  4002: {
    title: '없는 경매방이에요',
    description: '주소를 확인하거나 목록에서 다시 들어와 주세요.',
  },
  4004: {
    title: '이미 종료된 경매방이에요',
    description: '종료된 방에서는 물품을 바꿀 수 없어요.',
  },
  4005: {
    title: '이미 다른 경매방에 올라간 상품이에요',
    description: '그 경매방에서 먼저 빼고 다시 추가해 주세요.',
  },
  4006: {
    title: '시작된 물품은 뺄 수 없어요',
    description: '아직 시작하지 않은 물품만 뺄 수 있어요.',
  },
  4007: {
    title: '시작할 수 없는 물품이에요',
    description: '이미 진행 중이거나 끝난 물품이에요. 새로고침해 주세요.',
  },
  4008: {
    title: '이미 3개가 진행 중이에요',
    description: '한 경매방에서는 3개까지만 동시에 진행할 수 있어요.',
  },
  4009: {
    title: '물품을 더 넣을 수 없어요',
    description: '한 경매방에는 100개까지만 담을 수 있어요.',
  },
  4010: {
    title: '진행 중인 물품이 아니에요',
    description: '이미 마감됐을 수 있어요. 새로고침해 주세요.',
  },
  4011: {
    title: '이미 마감이 임박했어요',
    description: '연장 구간에 들어와서 더 앞당길 수 없어요.',
  },
  5001: {
    title: '없는 상품이에요',
    description: '상품 목록에서 빠졌을 수 있어요. 다시 골라주세요.',
  },
}

/**
 * code 로 문구를 찾는다.
 *
 * 벌크 추가는 실패를 예외로 던지지 않고 `failed[]` 안에 code 로 담아 준다.
 * 그 항목마다 사유를 붙이려면 응답 객체가 아니라 code 로 찾을 수 있어야 한다.
 */
export function sellerActionMessageByCode(
  code: number | undefined,
): SellerActionErrorMessage | undefined {
  return code === undefined ? undefined : BY_CODE[code]
}

/** 표에 없는 코드일 때 쓸 문구. 조작마다 제목이 다르다. */
const UNKNOWN: Record<SellerAction, SellerActionErrorMessage> = {
  add: {
    title: '물품을 추가하지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
  remove: {
    title: '물품을 빼지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
  start: {
    title: '경매를 시작하지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
  closeRoom: {
    title: '경매방을 종료하지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
  closeEarly: {
    title: '마감을 앞당기지 못했어요',
    description: '잠시 뒤에 다시 시도해 주세요.',
  },
}

export function toSellerActionErrorMessage(
  error: unknown,
  action: SellerAction,
): SellerActionErrorMessage {
  const code = readApiErrorCode(error)

  if (code === 'offline') {
    return {
      title: '연결이 끊겼어요',
      description: '네트워크를 확인하고 다시 시도해 주세요.',
    }
  }

  const known = sellerActionMessageByCode(
    typeof code === 'number' ? code : undefined,
  )

  if (import.meta.env.DEV && !known) {
    console.error('처리하지 않은 판매자 조작 에러', action, code, error)
  }

  return known ?? UNKNOWN[action]
}
