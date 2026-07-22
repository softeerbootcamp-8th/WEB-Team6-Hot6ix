import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios'

/**
 * Orval 이 생성한 모든 요청이 통과하는 공용 axios 인스턴스.
 * baseURL / 인터셉터 / 토큰 주입은 여기서 한 곳으로 관리한다.
 */
export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
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

/** Orval `mutator` 로 사용하는 커스텀 인스턴스 함수. */
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  const source = axios.CancelToken.source()
  const promise = axiosInstance({
    ...config,
    ...options,
    cancelToken: source.token,
  }).then(({ data }: AxiosResponse<T>) => data)

  // TanStack Query 취소 연동
  // @ts-expect-error orval 이 반환값에 cancel 을 첨부한다.
  promise.cancel = () => source.cancel('Query was cancelled')

  return promise
}

export type ErrorType<Error> = AxiosError<Error>
export type BodyType<BodyData> = BodyData

export default customInstance
