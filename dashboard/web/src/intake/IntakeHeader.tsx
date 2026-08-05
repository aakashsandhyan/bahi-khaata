import { Fragment } from 'react'
import type { LotIntakeStats } from '../types'
import { rupees } from '../money'

/**
 * The four header figures, from `GET /api/lots/{lotId}/stats` (design decision D5 of
 * palletworks-intake): amount paid, MRP found (cumulative, not a manifest total — D6), a
 * provisional cost-of-MRP percent, and counted x of y. Never a divide-by-zero: the backend
 * already answers null for a ratio it cannot compute honestly, and this component renders that
 * null as a dash rather than inventing a figure.
 *
 * A stats-fetch failure degrades only this strip (pallet-intake spec, "Loading and error states"
 * requirement) — the rail, step strip, and tabs keep reading their own, independent sources.
 */
export function IntakeHeader({
  stats,
  loading,
  failed,
}: {
  stats: LotIntakeStats | null
  loading: boolean
  failed: boolean
}) {
  // `stats` itself is the source of truth for whether there is anything real to show — `loading`
  // is only for phrasing. A lot switch clears `stats` to null before the new fetch resolves, and
  // that gap must never read `stats!` (a bare loading/failed flag lagging one render behind would
  // do exactly that and crash on the null).
  const dash = !stats

  return (
    <Fragment>
      <div className="intake-kpis">
        <div className="intake-kpi">
          <div className="intake-kpi-label">Amount paid</div>
          <div className="intake-kpi-value">{stats ? rupees(stats.paidPaise) : loading ? '…' : '—'}</div>
        </div>
        <div className="intake-kpi">
          <div className="intake-kpi-label">MRP found</div>
          <div className="intake-kpi-value">{stats ? rupees(stats.mrpFoundPaise) : loading ? '…' : '—'}</div>
          <div className="intake-kpi-sub">cumulative, over counted units</div>
        </div>
        <div className="intake-kpi">
          <div className="intake-kpi-label">Cost of MRP</div>
          <div className="intake-kpi-value">
            {dash ? (loading ? '…' : '—') : stats.costOfMrpPercent == null ? '—' : `${stats.costOfMrpPercent}%`}
          </div>
          <div className="intake-kpi-sub">provisional</div>
        </div>
        <div className="intake-kpi">
          <div className="intake-kpi-label">Counted</div>
          <div className="intake-kpi-value">
            {stats ? `${stats.countedUnits} of ${stats.expectedUnits ?? 0}` : loading ? '…' : '—'}
          </div>
        </div>
      </div>
      {failed && (
        <p className="intake-tab-note">Stats unavailable right now — the rest of the screen still works.</p>
      )}
    </Fragment>
  )
}
