import type { DeliveryProgress, LotSummary } from '../types'
import { activeStep } from './steps'

const STEPS: { key: 'manifest' | 'counting' | 'reconcile' | 'close'; label: (manual: boolean) => string }[] = [
  { key: 'manifest', label: (manual) => (manual ? 'Manual' : 'Manifest in') },
  { key: 'counting', label: () => 'Counting' },
  { key: 'reconcile', label: () => 'Reconcile' },
  { key: 'close', label: () => 'Close' },
]

/**
 * The four-step strip for the selected lot (design decision D4 of palletworks-intake). Advisory
 * framing only — the tabs, not this strip, drive every action (design.md Risks). Close is shown
 * as the remaining terminal action, never a resting "done" state: a closed lot has already left
 * the rail (D2), so a rail lot can never show Close as reached.
 */
export function IntakeSteps({ lot, delivery }: { lot: LotSummary; delivery: DeliveryProgress | null }) {
  const active = activeStep(lot, delivery)

  return (
    <div className="intake-steps">
      {STEPS.map((step) => {
        let state: 'done' | 'active' | '' = ''
        if (step.key === 'manifest') state = 'done'
        else if (step.key === active) state = 'active'
        else if (step.key === 'counting' && active === 'reconcile') state = 'done'
        return (
          <div key={step.key} className={`intake-step ${state}`}>
            {step.label(lot.isManual)}
          </div>
        )
      })}
    </div>
  )
}
