import { useState } from 'react'
import { receiving, BackendError } from '../api'
import type { DeliveryProgress, LotIntakeStats, LotSummary } from '../types'
import { rupees } from '../money'
import { activeStep } from './steps'

/**
 * Lot math over counted and priced units only (pallet-intake spec, "Right rail shows lot math"):
 * effective cost per unit (paid ÷ counted), short/over totals, MRP found, and projected retail —
 * never extrapolated across unpriced units, since the backend already excludes them (D5).
 *
 * The primary action is Close for a fully-counted lot or Receiving-finished for one still
 * receiving (D9). Close itself — including the unopened-cartons confirm gate — lives in the
 * Reconcile & close tab, so this button only switches there; Receiving-finished is a one-step
 * action a manual lot can take from here directly, the same action LotManagement offered before
 * this change.
 */
export function LotMathRail({
  lot,
  delivery,
  stats,
  onGoToReconcile,
  onChanged,
}: {
  lot: LotSummary
  delivery: DeliveryProgress | null
  stats: LotIntakeStats | null
  onGoToReconcile: () => void
  onChanged: () => void
}) {
  const [error, setError] = useState<string | null>(null)
  const fullyCounted = activeStep(lot, delivery) === 'reconcile'

  const markReceivingComplete = async () => {
    setError(null)
    try {
      await receiving.markReceivingComplete(lot.id)
      onChanged()
    } catch (err) {
      setError(err instanceof BackendError ? err.message : 'Could not mark receiving finished.')
    }
  }

  return (
    <div className="intake-math">
      <div className="intake-math-title">Lot math</div>

      <div className="intake-math-row">
        <span className="intake-math-label">Effective cost/unit</span>
        <span className="intake-math-value">
          {stats?.effectiveCostPerUnitPaise != null ? rupees(stats.effectiveCostPerUnitPaise) : '—'}
        </span>
      </div>
      <div className="intake-math-row">
        <span className="intake-math-label">Short</span>
        <span className="intake-math-value">{stats?.shortUnits ?? '—'}</span>
      </div>
      <div className="intake-math-row">
        <span className="intake-math-label">Over</span>
        <span className="intake-math-value">{stats?.overUnits ?? '—'}</span>
      </div>
      <div className="intake-math-row">
        <span className="intake-math-label">MRP found</span>
        <span className="intake-math-value">{stats ? rupees(stats.mrpFoundPaise) : '—'}</span>
      </div>
      <div className="intake-math-row">
        <span className="intake-math-label">Projected retail</span>
        <span className="intake-math-value">{stats ? rupees(stats.projectedRetailPaise) : '—'}</span>
      </div>

      {error && <p className="banner stop">{error}</p>}

      <div className="intake-math-action">
        {fullyCounted ? (
          <button type="button" className="btn-primary btn-block" onClick={onGoToReconcile}>
            Close…
          </button>
        ) : lot.isManual ? (
          <button type="button" className="btn-primary btn-block" onClick={markReceivingComplete}>
            Receiving finished
          </button>
        ) : (
          <button type="button" className="btn-primary btn-block" disabled>
            Still receiving
          </button>
        )}
      </div>
    </div>
  )
}
