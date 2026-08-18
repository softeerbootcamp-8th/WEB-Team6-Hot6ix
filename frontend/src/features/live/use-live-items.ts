import { useEffect, useState, type Dispatch, type SetStateAction } from 'react'

import type { AuctionLiveStateResponseDto } from '@/api/generated/model'
import type { AuctionItemDetail } from '@/types/domain'
import {
  applyLiveSnapshots,
  reconcileServerItems,
} from '@/features/live/live-state'

export function useLiveItems(
  serverItems: AuctionItemDetail[],
  serverUpdatedAt: number,
  snapshots: AuctionLiveStateResponseDto[],
  snapshotsUpdatedAt: number,
): [AuctionItemDetail[], Dispatch<SetStateAction<AuctionItemDetail[]>>] {
  const [items, setItems] = useState<AuctionItemDetail[]>(serverItems)

  useEffect(() => {
    if (serverUpdatedAt === 0) return
    setItems((current) =>
      applyLiveSnapshots(reconcileServerItems(current, serverItems), snapshots),
    )
  }, [serverItems, serverUpdatedAt, snapshots])

  useEffect(() => {
    if (snapshotsUpdatedAt === 0) return
    setItems((current) => applyLiveSnapshots(current, snapshots))
  }, [serverItems, snapshots, snapshotsUpdatedAt])

  return [items, setItems]
}
