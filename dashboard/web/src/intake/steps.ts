import type { DeliveryProgress, LotSummary } from '../types'

// The four-step strip, inferred frontend-side from data the backend already exposes — no new
// persisted state column (design decision D4 of palletworks-intake). Shared by IntakeSteps (the
// selected lot's own strip) and IntakeRail (a lighter per-lot badge over the same rule), so the
// two can never disagree.
export type IntakeStep = 'manifest' | 'counting' | 'reconcile'

/**
 * Which step is active for one lot right now.
 *
 * Step 1 (Manifest in / Manual) is always past once the lot exists, so it is never returned here.
 * Counting is active while a manifest lot has cartons still being counted (or fewer units counted
 * than expected); a manual lot has no boxes to go terminal, so `receivingComplete` is its gate
 * instead (D4). Reconcile is reached once counting is done — the lot is still open, since a
 * closed lot has already left the rail (D2).
 */
export function activeStep(lot: LotSummary, delivery: DeliveryProgress | null): IntakeStep {
  if (lot.isManual) {
    return lot.receivingComplete ? 'reconcile' : 'counting'
  }
  const stillCounting =
    !delivery ||
    delivery.cartonsFinished < delivery.cartonsTotal ||
    delivery.unitsCounted < delivery.unitsExpected
  return stillCounting ? 'counting' : 'reconcile'
}
