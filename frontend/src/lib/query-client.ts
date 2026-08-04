import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'

/** 앱 전역에서 공유하는 단일 QueryClient 인스턴스. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1분
      retry: (failureCount, error) => {
        if (isAxiosError(error) && error.response) {
          const status = error.response.status
          // 4xx 에러(클라이언트 에러)는 재시도하지 않음
          if (status >= 400 && status < 500) {
            return false
          }
        }
        return failureCount < 1
      },
      refetchOnWindowFocus: false,
    },
  },
})
