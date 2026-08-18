type BufferedEvent = { eventId?: number }

export function createLiveEventBuffer<T extends BufferedEvent>(
  deliver: (event: T) => void,
) {
  let buffering = true
  let arrivalOrder = 0
  let pending: Array<{ event: T; order: number }> = []

  return {
    receive(event: T) {
      if (!buffering) {
        deliver(event)
        return
      }
      pending.push({ event, order: arrivalOrder++ })
    },
    start() {
      buffering = true
    },
    drain() {
      const events = pending
      pending = []
      buffering = false
      events
        .sort((left, right) => {
          const leftId = left.event.eventId
          const rightId = right.event.eventId
          return leftId !== undefined && rightId !== undefined
            ? leftId - rightId
            : left.order - right.order
        })
        .forEach(({ event }) => deliver(event))
    },
  }
}
