import { isAxiosError } from 'axios'

import type { ValidationError } from '@/api/generated/model'

/**
 * 검증 실패 응답에서 "무엇이 왜 걸렸는지"를 꺼낸다.
 *
 * **여기서만 서버 문구를 그대로 쓴다.** 다른 에러는 서버 `message` 가 개발자
 * 기준이라 화면이 code 로 문구를 고르지만(`API-INTEGRATION.md` 3장), 검증 실패의
 * `errors[].message` 는 서버가 DTO 에 적어둔 사용자용 한국어다
 * (`경매방 이름은 100자 이하이며…`, `시작가는 1조원 이하여야 합니다`).
 *
 * 이게 없으면 길이 제한이든 형식 오류든 전부 `입력값을 다시 확인해 주세요` 하나로
 * 뭉쳐서, 사용자가 어느 칸을 고쳐야 하는지 알 방법이 없었다(#328).
 *
 * 필드 이름은 붙이지 않는다. 서버 문구가 이미 `경매방 이름은…` 처럼 대상을 말하고,
 * `name` 같은 영문 필드명을 앞에 달면 오히려 읽기 어려워진다.
 *
 * @returns 검증 실패가 아니거나 문구가 비어 있으면 `null`
 */
export function readValidationMessage(error: unknown): string | null {
  if (!isAxiosError(error)) return null

  const errors = (
    error.response?.data as { errors?: ValidationError[] } | undefined
  )?.errors

  if (!errors?.length) return null

  /*
   * 같은 문구가 여러 번 오는 경우가 있다. 목록 요청은 원소마다 검증이 돌아서
   * 같은 제약이 걸린 항목 수만큼 같은 줄이 쌓인다.
   */
  const messages = [
    ...new Set(
      errors
        .map(({ message }) => message)
        .filter((message): message is string => Boolean(message)),
    ),
  ]

  return messages.length > 0 ? messages.join(' ') : null
}
