export function leaderboardEntryKey(entry: {
  nickname: string
  bidderKey?: string | null
}): string {
  return entry.bidderKey
    ? `bidder:${entry.bidderKey}`
    : `nickname:${entry.nickname}`
}
