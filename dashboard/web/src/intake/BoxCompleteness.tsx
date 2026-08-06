import { useEffect, useState } from 'react'
import { unpacking } from '../api'
import type { CartonProgress } from '../types'

/**
 * Per-box completeness for the selected lot — not started, part counted, finished — reading the
 * same report the goods-in-reconciliation capability already defines, with no change to how
 * completeness is computed (goods-in-reconciliation spec, "Intake surfaces per-box completeness
 * for the selected lot"). `GET /api/unpacking/lots/{lotId}/boxes` already returns this per box;
 * this only buckets it the way an operator asks the question — how many of each.
 */
export function BoxCompleteness({ lotId }: { lotId: string }) {
  const [cartons, setCartons] = useState<CartonProgress[] | null>(null)

  useEffect(() => {
    setCartons(null)
    unpacking.boxesOf(lotId).then(setCartons).catch(() => setCartons([]))
  }, [lotId])

  if (!cartons) return null

  const notStarted = cartons.filter((c) => !c.finished && c.unitsCounted === 0 && c.unitsUnlisted === 0).length
  const inProgress = cartons.filter((c) => !c.finished && (c.unitsCounted > 0 || c.unitsUnlisted > 0)).length
  const finished = cartons.filter((c) => c.finished).length

  return (
    <div className="intake-recon-row">
      <span className="intake-math-label">Boxes — not started / part counted / finished</span>
      <span className="intake-math-value">{notStarted} / {inProgress} / {finished}</span>
    </div>
  )
}
