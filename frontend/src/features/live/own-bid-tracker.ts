export interface OwnBidTracker {
  remember: (itemId: number, amount: number) => void
  forget: (itemId: number, amount: number) => void
  has: (itemId: number, amount: number) => boolean
}

const bidKey = (itemId: number, amount: number) => `${itemId}:${amount}`

export const createOwnBidTracker = (): OwnBidTracker => {
  const bids = new Set<string>()

  return {
    remember(itemId, amount) {
      bids.add(bidKey(itemId, amount))
    },

    forget(itemId, amount) {
      bids.delete(bidKey(itemId, amount))
    },

    has(itemId, amount) {
      return bids.has(bidKey(itemId, amount))
    },
  }
}

export async function trackOwnBidAttempt<T>(
  tracker: OwnBidTracker,
  itemId: number,
  amount: number,
  request: () => Promise<T>,
): Promise<T> {
  tracker.remember(itemId, amount)

  try {
    return await request()
  } catch (error) {
    tracker.forget(itemId, amount)
    throw error
  }
}
