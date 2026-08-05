import { useEffect, useState } from 'react'
import { api, inventory, printer, BackendError } from './api'
import type { InventoryBatchLine, InventoryDetail } from './types'
import { rupees } from './money'

/**
 * One product's full story: batches, ledger movements, and price history composed into a single
 * payload (design decision D3 of palletworks-inventory) — opened from an Inventory row or the
 * Catalog panel, carrying a product id rather than living inside either screen (D9).
 *
 * Reprice goes through {@link api.setPrice} — the same `PUT /api/products/{id}/price` endpoint
 * the Catalog panel already uses, which is itself the shelf-pricing choke point
 * (`ProductPricing.setSellingPrice`). There is no second price-write path here; a successful
 * reprice reloads the detail so the new price-history row (and the fresh KPI figures) show
 * immediately, proving the journal through the same stack a person would use.
 */
export function ItemDetail({ productId, onBack }: { productId: string; onBack: () => void }) {
  const [detail, setDetail] = useState<InventoryDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setError(null)
    inventory
      .detail(productId)
      .then(setDetail)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [productId])

  if (error) {
    return (
      <div className="pad">
        <p className="banner stop">{error}</p>
      </div>
    )
  }
  if (!detail) return <div className="pad">Loading…</div>

  const conditions = Array.from(new Set(detail.batches.map((b) => b.condition)))
  const newestBatch = detail.batches[0] as InventoryBatchLine | undefined

  return (
    <div className="page item-detail">
      <button type="button" className="back" onClick={onBack}>
        ← Back to Inventory
      </button>

      <h2>{detail.productName}</h2>
      <p className="cat-detail-meta">
        {detail.categoryCode}
        {conditions.map((c) => (
          <span key={c} className={`cat-badge ${c === 'DAMAGED' ? 'cat-warn' : 'cat-good'}`}>
            {c}
          </span>
        ))}
      </p>
      <p className="id-barcodes">
        {detail.barcodes.length === 0
          ? 'No barcode on record.'
          : detail.barcodes.map((code) => (
              <span key={code} className="cat-code">{code}</span>
            ))}
      </p>

      <div className="id-kpis">
        <div className="id-kpi">
          <div className="id-kpi-label">Cost basis</div>
          <div className="id-kpi-value">{rupees(detail.costBasisPaise)}</div>
        </div>
        <div className="id-kpi">
          <div className="id-kpi-label">Price</div>
          <div className="id-kpi-value">{rupees(detail.sellingPricePaise)}</div>
        </div>
        <div className="id-kpi">
          <div className="id-kpi-label">Margin</div>
          <div className="id-kpi-value">
            {detail.marginPercent != null ? `${detail.marginPercent}%` : '—'}
          </div>
        </div>
        <div className="id-kpi">
          <div className="id-kpi-label">Sold / Received</div>
          <div className="id-kpi-value">{detail.soldUnits}/{detail.receivedUnits}</div>
        </div>
      </div>

      <ActionsRail
        productId={productId}
        currentPricePaise={detail.sellingPricePaise}
        barcode={detail.barcodes[0] ?? null}
        productName={detail.productName}
        mrpPaise={newestBatch?.mrpPaise ?? null}
        onChanged={load}
      />

      <h3 className="cat-sub-head">Movement log</h3>
      {detail.movements.length === 0 && <p className="empty">No stock movements recorded.</p>}
      {detail.movements.length > 0 && (
        <table className="id-table">
          <thead>
            <tr>
              <th className="name">Type</th>
              <th>Quantity</th>
              <th>COGS</th>
              <th className="name">When</th>
            </tr>
          </thead>
          <tbody>
            {detail.movements.map((m, i) => (
              <tr key={i}>
                <td className="name">{m.movementType}</td>
                <td className={m.quantity < 0 ? 'stop' : 'good'}>
                  {m.quantity > 0 ? `+${m.quantity}` : m.quantity}
                </td>
                <td>{rupees(m.cogsPaise)}</td>
                <td className="name">{new Date(m.effectiveAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3 className="cat-sub-head">Price history</h3>
      {detail.priceHistory.length === 0 && <p className="empty">No price changes recorded.</p>}
      {detail.priceHistory.length > 0 && (
        <table className="id-table">
          <thead>
            <tr>
              <th className="name">From</th>
              <th className="name">To</th>
              <th className="name">Operator</th>
              <th className="name">When</th>
            </tr>
          </thead>
          <tbody>
            {detail.priceHistory.map((p, i) => (
              <tr key={i}>
                <td className="name">
                  {p.oldPricePaise == null ? <span className="tag tag-neutral">First price</span> : rupees(p.oldPricePaise)}
                </td>
                <td className="name">{rupees(p.newPricePaise)}</td>
                <td className="name">{p.operatorName ?? '—'}</td>
                <td className="name">{new Date(p.changedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3 className="cat-sub-head">Batches</h3>
      {detail.batches.length === 0 && <p className="empty">No batches recorded.</p>}
      {detail.batches.length > 0 && (
        <table className="id-table">
          <thead>
            <tr>
              <th className="name">Condition</th>
              <th className="name">Lot</th>
              <th className="name">Bin</th>
              <th>Received</th>
              <th>Damaged</th>
              <th>Unit cost</th>
              <th>MRP</th>
            </tr>
          </thead>
          <tbody>
            {detail.batches.map((b) => (
              <BatchRow key={b.batchId} batch={b} onBinSaved={load} />
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function BatchRow({ batch, onBinSaved }: { batch: InventoryBatchLine; onBinSaved: () => void }) {
  const [bin, setBin] = useState(batch.bin ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      await inventory.setBin(batch.batchId, bin)
      onBinSaved()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Could not save the bin.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <tr>
      <td className="name">{batch.condition}</td>
      <td className="name">{batch.lotLabel}</td>
      <td className="name id-bin-cell">
        <input
          className="id-bin-input"
          value={bin}
          placeholder="—"
          onChange={(e) => setBin(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && save()}
        />
        <button type="button" className="btn-ghost" disabled={saving} onClick={save}>
          {saving ? '…' : 'Save'}
        </button>
        {error && <div className="id-bin-error">{error}</div>}
      </td>
      <td>{batch.quantityReceived}</td>
      <td>{batch.quantityDamaged}</td>
      <td>{rupees(batch.allocatedUnitCostPaise)}</td>
      <td>{rupees(batch.mrpPaise)}</td>
    </tr>
  )
}

function ActionsRail({
  productId,
  currentPricePaise,
  barcode,
  productName,
  mrpPaise,
  onChanged,
}: {
  productId: string
  currentPricePaise: number | null
  barcode: string | null
  productName: string
  mrpPaise: number | null
  onChanged: () => void
}) {
  const [price, setPrice] = useState(currentPricePaise != null ? (currentPricePaise / 100).toString() : '')
  const [repricing, setRepricing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)
  const [queuing, setQueuing] = useState(false)

  const reprice = async () => {
    const n = Number(price)
    if (!Number.isFinite(n) || n <= 0) {
      setError('Enter a rupee amount like 249 or 249.50.')
      return
    }
    setRepricing(true)
    setError(null)
    setNote(null)
    try {
      await api.setPrice(productId, Math.round(n * 100))
      setNote('Price saved.')
      onChanged()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Could not set the price.')
    } finally {
      setRepricing(false)
    }
  }

  const queueReprint = async () => {
    if (!barcode) return
    setQueuing(true)
    setError(null)
    setNote(null)
    try {
      await printer.queueLabel({
        barcode,
        productName,
        sellingPricePaise: currentPricePaise ?? 0,
        mrpPaise,
        copies: 1,
        productId,
      })
      setNote('Label reprint queued.')
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Could not queue the label.')
    } finally {
      setQueuing(false)
    }
  }

  return (
    <div className="id-actions">
      <div className="id-action">
        <label className="id-action-label">Reprice</label>
        <div className="cat-price-rule">
          <input
            className="price-in"
            placeholder="₹"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && reprice()}
          />
          <button type="button" className="btn-primary" disabled={repricing} onClick={reprice}>
            {repricing ? 'Saving…' : 'Save price'}
          </button>
        </div>
      </div>
      <div className="id-action">
        <label className="id-action-label">Label</label>
        <button type="button" className="btn-ghost" disabled={!barcode || queuing} onClick={queueReprint}>
          {barcode ? (queuing ? 'Queuing…' : 'Queue label reprint') : 'No barcode to print'}
        </button>
      </div>
      {error && <p className="stop">{error}</p>}
      {note && <p className="banner ok">{note}</p>}
    </div>
  )
}
