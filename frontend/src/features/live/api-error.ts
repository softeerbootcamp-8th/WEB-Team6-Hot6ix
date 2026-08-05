import { isAxiosError } from 'axios'

/**
 * 실패 응답에서 서버 `ErrorType` 의 code 를 꺼낸다.
 *
 * 서버가 보낸 `message` 는 쓰지 않는다. 개발자 기준이라 "그래서 뭘 하면
 * 되는지"가 없다 (`API-INTEGRATION.md` 3장). code 로 화면 문구를 고른다.
 *
 * @returns 응답이 아예 없으면(네트워크 끊김) `'offline'`,
 *          code 를 못 읽으면 `undefined`
 */
export function readApiErrorCode(
  error: unknown,
): number | 'offline' | undefined {
  if (!isAxiosError(error)) return undefined
  if (!error.response) return 'offline'

  return (error.response.data as { code?: number } | undefined)?.code
}

/**
 * 네트워크 오류로 실패했을 때만 한 번 더 보낸다.
 *
 * 서버가 판정해서 거절한 요청은 다시 보내도 결과가 같으므로 그대로 던진다.
 * 응답이 아예 없었던 경우만 — 요청이 서버에 닿았는지조차 모르는 상태다.
 *
 * TanStack Query 의 `retry` 옵션을 쓰지 않는 이유는 재시도 횟수를 여기서
 * 눈에 보이게 두려는 것이다. 판매자 조작은 방송 중에 일어나므로 몇 번
 * 나가는지가 코드에 드러나 있어야 한다.
 */
export async function retryOnNetworkError<T>(
  send: () => Promise<T>,
): Promise<T> {
  try {
    return await send()
  } catch (error) {
    if (readApiErrorCode(error) !== 'offline') throw error
    return send()
  }
}
