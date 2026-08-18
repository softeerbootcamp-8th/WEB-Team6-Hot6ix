export function eventIdFromLastEventId(
  kind: string,
  lastEventId: string,
): number | undefined {
  if (kind === 'ParticipantCount') return undefined

  const eventId = Number(lastEventId)
  return Number.isSafeInteger(eventId) && eventId > 0 ? eventId : undefined
}
