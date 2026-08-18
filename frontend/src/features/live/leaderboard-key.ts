export function leaderboardEntryKey(entry: {
  nickname: string
  bidderKey?: string | null
  rank?: number
}): string {
  return entry.bidderKey
    ? `bidder:${entry.bidderKey}`
    : `nickname:${entry.nickname}:rank:${entry.rank ?? 'unknown'}`
}
