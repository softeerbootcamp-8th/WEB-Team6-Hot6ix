import { QueryClient } from '@tanstack/react-query'

/** 앱 전역에서 공유하는 단일 QueryClient 인스턴스. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000, // 1분
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
