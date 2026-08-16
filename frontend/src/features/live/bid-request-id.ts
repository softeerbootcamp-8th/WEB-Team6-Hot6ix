export interface BidRequestIdTracker {
  acquire: (itemId: number, amount: number) => string
  complete: (requestId: string) => void
}

export const createBidRequestIdTracker = (
  createId: () => string = () => crypto.randomUUID(),
): BidRequestIdTracker => {
  const requestByIntent = new Map<string, string>()
  const intentByRequest = new Map<string, string>()

  return {
    acquire(itemId, amount) {
      const intent = `${itemId}:${amount}`
      const existing = requestByIntent.get(intent)
      if (existing) return existing

      const requestId = createId()
      requestByIntent.set(intent, requestId)
      intentByRequest.set(requestId, intent)
      return requestId
    },

    complete(requestId) {
      const intent = intentByRequest.get(requestId)
      if (!intent) return

      requestByIntent.delete(intent)
      intentByRequest.delete(requestId)
    },
  }
}
