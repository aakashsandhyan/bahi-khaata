import { useMemo } from 'react'
import type { DeliveryProgress, LotSummary } from '../types'
import { activeStep } from './steps'

const STEP_LABEL: Record<string, string> = { counting: 'Counting', reconcile: 'Reconcile' }

/**
 * The lot rail: open lots only, from `receiving.lots()` — already filtered to `Lot::isOpen`
 * (design decision D2 of palletworks-intake), so a closed lot drops off on its own without this
 * component doing anything special. Exactly one lot is selected at a time and drives every other
 * section (pallet-intake spec, "Lot rail lists open lots and drives every section").
 */
export function IntakeRail({
  lots,
  deliveries,
  selectedLotId,
  onSelect,
  onNewLot,
}: {
  lots: LotSummary[] | null
  deliveries: DeliveryProgress[]
  selectedLotId: string | null
  onSelect: (lotId: string) => void
  onNewLot: () => void
}) {
  const deliveryByLot = useMemo(() => {
    const map = new Map<string, DeliveryProgress>()
    for (const d of deliveries) map.set(d.lotId, d)
    return map
  }, [deliveries])

  return (
    <div className="intake-rail">
      <div className="intake-rail-head">
        <span className="intake-rail-title">Open lots</span>
        <button type="button" className="btn-ghost" onClick={onNewLot} style={{ padding: '2px 6px' }}>
          + New lot
        </button>
      </div>

      {lots === null ? (
        <p className="intake-rail-empty">Loading…</p>
      ) : lots.length === 0 ? (
        <p className="intake-rail-empty">No open lots.</p>
      ) : (
        <div className="intake-rail-list">
          {lots.map((lot) => {
            const step = activeStep(lot, deliveryByLot.get(lot.id) ?? null)
            return (
              <button
                key={lot.id}
                type="button"
                className={lot.id === selectedLotId ? 'intake-rail-item on' : 'intake-rail-item'}
                onClick={() => onSelect(lot.id)}
              >
                <span className="intake-rail-name">
                  {lot.supplier}
                  {lot.isManual && <span className="text-muted"> (M)</span>}
                </span>
                <span className="intake-rail-meta">
                  <span>{lot.receivedOn}</span>
                  <span className={`cat-badge ${step === 'reconcile' ? 'cat-good' : 'cat-warn'}`}>
                    {STEP_LABEL[step]}
                  </span>
                </span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
