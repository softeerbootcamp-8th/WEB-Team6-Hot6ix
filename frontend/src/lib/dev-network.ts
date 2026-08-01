import { AxiosError, type AxiosRequestConfig } from 'axios'

import { devFlagsStore } from '@/lib/dev-flags'

/**
 * 개발용 네트워크 시뮬레이션을 요청 앞단에 적용한다.
 *
 * `custom-instance` 가 모든 요청 전에 한 번 부른다. 프로덕션에서는
 * `import.meta.env.DEV` 가 false 라 함수 본문이 통째로 제거된다.
 *
 * 실패는 서버가 500 을 준 것처럼 흉내 내므로, 화면의 에러 처리·재시도를
 * 실제 상황과 같은 경로로 확인할 수 있다.
 */
export async function applyDevNetworkFlags(
  config: AxiosRequestConfig,
): Promise<void> {
  if (!import.meta.env.DEV) return

  const { delayMs, failRate } = devFlagsStore.get()

  if (delayMs > 0) {
    await new Promise((resolve) => window.setTimeout(resolve, delayMs))
  }

  if (failRate > 0 && Math.random() * 100 < failRate) {
    throw new AxiosError(
      `[DEV] 강제 실패 (${config.method?.toUpperCase()} ${config.url})`,
      'ERR_DEV_FORCED_FAILURE',
      config as never,
      null,
      {
        status: 500,
        statusText: 'Dev Forced Failure',
        data: { message: '개발용 강제 실패입니다.' },
        headers: {},
        config: config as never,
      },
    )
  }
}
