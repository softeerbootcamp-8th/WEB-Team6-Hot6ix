/** 서버 event ID가 같은 최근 조회와 실시간 수신을 한 피드 항목으로 합친다. */
export function mergeRecentRoomEvents<T extends { id: number }>(
  recent: T[],
  live: T[],
): T[] {
  const knownIds = new Set(recent.map((event) => event.id))
  return [
    ...recent,
    ...live.filter((event) => {
      if (knownIds.has(event.id)) return false
      knownIds.add(event.id)
      return true
    }),
  ]
}
