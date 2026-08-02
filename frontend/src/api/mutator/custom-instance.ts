import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios'

import { applyDevNetworkFlags } from '@/lib/dev-network'

/**
 * Orval 이 생성한 모든 요청이 통과하는 공용 axios 인스턴스.
 * baseURL / 인터셉터 / 토큰 주입은 여기서 한 곳으로 관리한다.
 */
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  // 인증이 세션 쿠키(SESSION, httpOnly) 방식이라 켠다. dev 는 vite 프록시로
  // 동일 출처가 되어 없어도 되지만, baseURL 이 다른 호스트를 가리키는 순간
  // 이게 없으면 쿠키가 안 실려 로그인한 사용자도 401 을 받는다.
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 예: 요청 인터셉터 (인증 토큰 주입 지점)
axiosInstance.interceptors.request.use((config) => {
  // const token = ...
  // if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * Orval `mutator` 로 사용하는 커스텀 인스턴스 함수.
 *
 * 요청 직전에 개발용 지연·강제 실패를 한 번 통과시킨다(개발 빌드 한정).
 * 덕분에 백엔드를 멈추지 않고도 로딩·에러 화면을 확인할 수 있다.
 */
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const source = axios.CancelToken.source()

  const run = async (): Promise<T> => {
    await applyDevNetworkFlags(config)
    const response: AxiosResponse<T> = await axiosInstance({
      ...config,
      ...options,
      cancelToken: source.token,
    })
    return response.data
  }

  const promise = run()

  // TanStack Query 취소 연동
  // @ts-expect-error orval 이 반환값에 cancel 을 첨부한다.
  promise.cancel = () => source.cancel('Query was cancelled')

  return promise
}

export type ErrorType<Error> = AxiosError<Error>
export type BodyType<BodyData> = BodyData

export default customInstance
