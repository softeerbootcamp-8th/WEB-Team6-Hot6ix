type BufferedEvent = { eventId?: number }

export function createLiveEventBuffer<T extends BufferedEvent>(
  deliver: (event: T) => void,
  isTerminal: (event: T) => boolean = () => false,
) {
  let buffering = true
  let pending: T[] = []

  function drain() {
    const pendingWithIds = pending
      .filter((event) => event.eventId !== undefined)
      .sort(
        (left, right) => (left.eventId as number) - (right.eventId as number),
      )
    let idIndex = 0
    const events = pending.map((event) =>
      event.eventId === undefined ? event : pendingWithIds[idIndex++],
    )
    pending = []
    buffering = false
    events.forEach(deliver)
  }

  return {
    receive(event: T) {
      if (!buffering) {
        deliver(event)
        return
      }
      pending.push(event)
      if (isTerminal(event)) drain()
    },
    start() {
      buffering = true
    },
    drain,
  }
}
