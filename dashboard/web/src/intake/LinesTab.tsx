import { useEffect, useState } from 'react'
import { catalog, BackendError } from '../api'
import type { CatalogEntry, LotSummary } from '../types'

/**
 * The manifest reconciliation table for a manifest lot: Product, Expected, Counted, Δ — exactly
 * four columns, deliberately no grade/condition and no MRP/list-price column, since neither is on
 * `CatalogEntry` and there is no grade or list-price at intake (design decision D8 of
 * palletworks-intake). A manual lot has no manifest to reconcile against (D7) — its discovered
 * lines and add-product entry live in the Boxes tab instead, so this tab points there rather than
 * duplicating the same data in a second shape.
 */
export function LinesTab({ lot }: { lot: LotSummary }) {
  const [entries, setEntries] = useState<CatalogEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (lot.isManual) return
    setEntries(null)
    setError(null)
    catalog
      .browse('', 'all', '', 0, 200, lot.id)
      .then(setEntries)
      .catch((err) => setError(err instanceof BackendError ? err.message : 'Cannot reach the backend.'))
  }, [lot.id, lot.isManual])

  if (lot.isManual) {
    return (
      <p className="intake-tab-note">
        A manual lot has no manifest — counting is the manifest. See the Boxes tab to view the
        discovered lines and add products.
      </p>
    )
  }

  if (error) return <p className="banner stop">{error}</p>
  if (!entries) return <p className="intake-rail-empty">Loading…</p>

  return (
    <div className="inv-table-wrap">
      <table className="inv-table">
        <thead>
          <tr>
            <th className="name">Product</th>
            <th>Expected</th>
            <th>Counted</th>
            <th>Δ</th>
          </tr>
        </thead>
        <tbody>
          {entries.length === 0 && (
            <tr>
              <td colSpan={4} className="inv-empty">Nothing on this lot's manifest yet.</td>
            </tr>
          )}
          {entries.map((e) => {
            const delta = e.unitsCounted - e.unitsExpected
            return (
              <tr key={e.productId}>
                <td className="name"><span className="inv-name">{e.name}</span></td>
                <td>{e.unitsExpected}</td>
                <td>{e.unitsCounted}</td>
                <td>{delta > 0 ? `+${delta}` : delta}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
