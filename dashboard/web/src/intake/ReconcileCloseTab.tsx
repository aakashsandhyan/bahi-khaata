import { useEffect, useState } from 'react'
import { receiving, unpacking, BackendError } from '../api'
import type { LotIntakeStats, LotSummary } from '../types'
import { rupees } from '../money'
import { BoxCompleteness } from './BoxCompleteness'

/**
 * Goods-in cross-check + the close gate + a pricing hand-off (design decision D9 of
 * palletworks-intake). `LotReconcile` (the pricing-time phantom write-off tool) is deliberately
 * NOT embedded here — at intake it would always report zero, since it nets double-counts that
 * only exist after pricing.
 *
 * Closing over unopened cartons is never hard-blocked (goods that never arrive would otherwise
 * hold a lot open forever) — it requires a deliberate second click instead: the list is surfaced
 * first, and only an explicit "close anyway" confirms with `confirm=true`.
 */
export function ReconcileCloseTab({
  lot,
  stats,
  onGoToPricing,
  onChanged,
}: {
  lot: LotSummary
  stats: LotIntakeStats | null
  onGoToPricing: () => void
  onChanged: () => void
}) {
  const [unopened, setUnopened] = useState<string[] | null>(null)
  const [wantsConfirm, setWantsConfirm] = useState(false)
  const [closing, setClosing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setUnopened(null)
    setWantsConfirm(false)
    unpacking.unopened(lot.id).then(setUnopened).catch(() => setUnopened([]))
  }, [lot.id])

  const doClose = async (confirm: boolean) => {
    setClosing(true)
    setError(null)
    try {
      await unpacking.closeLot(lot.id, confirm)
      onChanged()
    } catch (err) {
      setError(err instanceof BackendError ? err.message : 'Could not close the lot.')
    } finally {
      setClosing(false)
      setWantsConfirm(false)
    }
  }

  const requestClose = () => {
    if (unopened && unopened.length > 0) {
      setWantsConfirm(true)
      return
    }
    doClose(false)
  }

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
    <div>
      {error && <div className="banner stop">{error}</div>}

      <BoxCompleteness lotId={lot.id} />
      <div className="intake-recon-row">
        <span className="intake-math-label">Short units</span>
        <span className="intake-math-value">{stats?.shortUnits ?? '—'}</span>
      </div>
      <div className="intake-recon-row">
        <span className="intake-math-label">Over units</span>
        <span className="intake-math-value">{stats?.overUnits ?? '—'}</span>
      </div>
      <div className="intake-recon-row">
        <span className="intake-math-label">Paid vs. pinned</span>
        <span className="intake-math-value">
          {stats ? `${rupees(stats.paidPaise)} vs. ${rupees(stats.pinnedPaise)}` : '—'}
        </span>
      </div>

      {unopened && unopened.length > 0 && (
        <div className="intake-unopened">
          {unopened.length} carton{unopened.length === 1 ? '' : 's'} never opened — closing now
          gives their goods no share of what was paid.
          <ul>
            {unopened.map((tracking) => <li key={tracking}>{tracking}</li>)}
          </ul>
        </div>
      )}

      <p className="intake-tab-note">
        Counted goods awaiting a price can be worked from{' '}
        <button type="button" className="btn-ghost" onClick={onGoToPricing} style={{ padding: 0 }}>
          Pricing →
        </button>
      </p>

      <div className="actions">
        {lot.isManual && !lot.receivingComplete && (
          <button type="button" className="btn-primary" onClick={markReceivingComplete} style={{ flex: 1 }}>
            Receiving finished
          </button>
        )}
        {!wantsConfirm ? (
          <button type="button" className="btn-primary" onClick={requestClose} disabled={closing} style={{ flex: 1 }}>
            Close lot
          </button>
        ) : (
          <>
            <button type="button" className="btn-warn" onClick={() => doClose(true)} disabled={closing} style={{ flex: 1 }}>
              Close anyway ({unopened?.length} unopened)
            </button>
            <button type="button" className="btn-ghost" onClick={() => setWantsConfirm(false)} style={{ flex: 1 }}>
              Cancel
            </button>
          </>
        )}
      </div>
    </div>
  )
}
