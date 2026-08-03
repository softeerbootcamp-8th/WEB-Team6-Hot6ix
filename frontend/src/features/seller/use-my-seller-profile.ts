import { useGetMyProfile } from '@/api/generated/판매자-프로필/판매자-프로필'

/**
 * 로그인한 회원의 판매자 프로필.
 *
 * `GET /seller-profiles/me` 의 **404 는 오류가 아니라 "아직 등록하지 않았다"** 는
 * 정상 상태다. 그래서 재시도하지 않고 `notFound` 로 갈라 준다. 화면은 이 값으로
 * 미등록 안내를 그리고, `isError` 는 진짜 장애일 때만 참이 된다.
 */
export function useMySellerProfile() {
  const { data, isPending, isError, error, refetch } = useGetMyProfile({
    query: {
      retry: (failureCount, err) =>
        err.response?.status !== 404 && failureCount < 1,
    },
  })

  const notFound = error?.response?.status === 404

  return {
    profile: data?.data ?? null,
    isPending,
    /** 프로필을 아직 등록하지 않았다. */
    notFound,
    /** 재시도로 풀릴 수 있는 진짜 오류. */
    isError: isError && !notFound,
    error,
    refetch,
  }
}
