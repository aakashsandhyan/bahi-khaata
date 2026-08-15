import { useState } from 'react'
import { receiving, BackendError } from '../api'
import type { LotIntakeStats, LotSummary, ReceivingBoxes } from '../types'
import { rupees } from '../money'
import { QtyInput } from '../QtyInput'

const CATEGORY_OPTIONS = [
  { value: 'KITCHEN', label: 'Kitchen' },
  { value: 'PERSONAL_CARE', label: 'Personal Care' },
  { value: 'HOME_ESSENTIALS', label: 'Home' },
  { value: 'WIRELESS', label: 'Electronics' },
]

const EMPTY_FORM = { code: '', name: '', quantity: 1, categoryCode: 'KITCHEN', estimatedCost: '' }

/**
 * A manual lot's counting-is-the-manifest framing, extracted out of {@link BoxesTab} to keep it
 * under the component size cap (design decision D1 of palletworks-intake). There is no manifest
 * to reconcile against (design decision D7): adding a product both discovers the line and counts
 * it, and the provisional cost per unit (paid ÷ counted, from the stats endpoint) falls as more
 * is added — the same behavior the retired Receiving screen's manual branch had.
 */
export function ManualLotPanel({
  lot,
  boxes,
  stats,
  onAdded,
}: {
  lot: LotSummary
  boxes: ReceivingBoxes | null
  stats: LotIntakeStats | null
  onAdded: (text: string, tone: string) => void
}) {
  const [form, setForm] = useState(EMPTY_FORM)

  const addProduct = async () => {
    if (!form.name || form.quantity <= 0) {
      onAdded('Fill required fields', 'stop')
      return
    }
    try {
      await receiving.addProduct(
        lot.id,
        form.code || null,
        form.name,
        form.quantity,
        form.categoryCode,
        form.estimatedCost ? Math.round(parseFloat(form.estimatedCost) * 100) : null,
      )
      setForm(EMPTY_FORM)
      onAdded(`✓ ${form.name} added`, 'ok')
    } catch (err) {
      onAdded(err instanceof BackendError ? err.message : 'Error adding product.', 'stop')
    }
  }

  return (
    <>
      <p className="intake-tab-note">
        A manual lot has no manifest — counting is the manifest. Adding a product both discovers
        the line and counts it; the provisional cost per unit falls as more is added.
      </p>
      {boxes && (
        <div className="intake-recon-row">
          <span className="intake-math-label">Discovered so far</span>
          <span className="intake-math-value">
            {boxes.counts.received} products
            {stats?.effectiveCostPerUnitPaise != null && (
              <> · {rupees(stats.effectiveCostPerUnitPaise)}/unit (provisional)</>
            )}
          </span>
        </div>
      )}

      <div className="id-actions">
        <div className="id-action">
          <label className="id-action-label">Product name *</label>
          <input
            type="text"
            placeholder="e.g., Face Cream"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </div>
        <div className="id-action">
          <label className="id-action-label">Category *</label>
          <select value={form.categoryCode} onChange={(e) => setForm({ ...form, categoryCode: e.target.value })}>
            {CATEGORY_OPTIONS.map((c) => (
              <option key={c.value} value={c.value}>{c.label}</option>
            ))}
          </select>
        </div>
        <div className="id-action">
          <label className="id-action-label">Qty *</label>
          <QtyInput min={1} value={form.quantity} onChange={(n) => setForm({ ...form, quantity: n })} />
        </div>
        <div className="id-action">
          <label className="id-action-label">Est. cost (₹)</label>
          <input
            type="number"
            placeholder="Optional"
            value={form.estimatedCost}
            onChange={(e) => setForm({ ...form, estimatedCost: e.target.value })}
          />
        </div>
      </div>
      <button type="button" onClick={addProduct} className="btn-primary btn-block">
        Add product
      </button>
    </>
  )
}
