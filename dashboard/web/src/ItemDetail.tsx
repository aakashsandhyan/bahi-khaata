import { useEffect, useState } from 'react'
import { api, inventory, printer, productCounting, shelfPricing, unpacking, BackendError } from './api'
import type { DeliveryProgress, InventoryBatchLine, InventoryDetail } from './types'
import { rupees } from './money'
import { ProductCountPane } from './ProductCountPane'

/**
 * One product's full story: batches, ledger movements, and price history composed into a single
 * payload (design decision D3 of palletworks-inventory) — opened from an Inventory row, carrying
 * a product id rather than living inside the Inventory screen itself (D9 of palletworks-inventory,
 * carried forward as the sole opener once the Catalog panel that used to share this job is deleted
 * — D9 of palletworks-nav).
 *
 * Reprice goes through {@link api.setPrice} — the same `PUT /api/products/{id}/price` endpoint
 * the deleted Catalog panel used, which is itself the shelf-pricing choke point
 * (`ProductPricing.setSellingPrice`). There is no second price-write path here; a successful
 * reprice reloads the detail so the new price-history row (and the fresh KPI figures) show
 * immediately, proving the journal through the same stack a person would use.
 *
 * Openable for an on-paper product too (D6 of palletworks-nav): `inventory.detail` resolves a
 * product's identity even with no stock behind it yet, and every stock-dependent section below
 * already renders its own honest empty state rather than a fabricated row — so an uncounted
 * product opened here is an intentional landing on the way to the Count action, not a broken one.
 */
export function ItemDetail({ productId, onBack }: { productId: string; onBack: () => void }) {
  const [detail, setDetail] = useState<InventoryDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  // The lot chosen by the Count entry's picker, or null while not counting (design decision D5 of
  // palletworks-nav). Set, this replaces the whole page with `ProductCountPane` — the same
  // full-panel swap the deleted Catalog screen did for its own Count button.
  const [counting, setCounting] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)

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

  if (counting) {
    return (
      <ProductCountPane
        lotId={counting}
        productId={productId}
        onClose={() => setCounting(null)}
        onProgress={load}
        onDone={(message) => {
          setCounting(null)
          load()
          setNote(message)
        }}
      />
    )
  }

  const conditions = Array.from(new Set(detail.batches.map((b) => b.condition)))
  const newestBatch = detail.batches[0] as InventoryBatchLine | undefined

  return (
    <div className="page item-detail">
      <button type="button" className="back" onClick={onBack}>
        ← Back to Inventory
      </button>

      {note && <div className="banner ok">{note}</div>}

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
        categoryCode={detail.categoryCode}
        onChanged={load}
        onCount={setCounting}
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
  categoryCode,
  onChanged,
  onCount,
}: {
  productId: string
  currentPricePaise: number | null
  barcode: string | null
  productName: string
  mrpPaise: number | null
  categoryCode: string
  onChanged: () => void
  // Opens the counting grid for the given lot — lifted to ItemDetail, which swaps the whole page
  // for ProductCountPane (design decision D5 of palletworks-nav).
  onCount: (lotId: string) => void
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
      <CategoryEditor productId={productId} categoryCode={categoryCode} onChanged={onChanged} />
      <CountEntry productId={productId} onCount={onCount} />
      {error && <p className="stop">{error}</p>}
      {note && <p className="banner ok">{note}</p>}
    </div>
  )
}

/**
 * A plain reclassification (design decision D4 of palletworks-nav): changes only the department,
 * through `PATCH /api/products/{id}/category` → `Product.setCategory`. It never touches price or
 * stock, so it does not go anywhere near the reprice action above or the shelf-pricing save path.
 * The category list is the shop's full list (`shelfPricing.categories`, the same source the
 * pricing screens already use), not a narrower, possibly-stale department subset.
 */
function CategoryEditor({
  productId,
  categoryCode,
  onChanged,
}: {
  productId: string
  categoryCode: string
  onChanged: () => void
}) {
  const [categories, setCategories] = useState<string[]>([])
  const [value, setValue] = useState(categoryCode)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)

  useEffect(() => {
    shelfPricing.categories().then(setCategories).catch(() => setCategories([]))
  }, [])
  // The product prop can change under an unmounted editor only if the same ActionsRail instance
  // is reused for a different product, which App never does (a fresh productId always remounts
  // ItemDetail) — kept anyway so a reload's fresh categoryCode is never shadowed by stale local
  // edit state.
  useEffect(() => setValue(categoryCode), [categoryCode])

  const save = async () => {
    if (value === categoryCode) return
    setSaving(true)
    setError(null)
    setNote(null)
    try {
      await api.setCategory(productId, value)
      setNote('Department saved.')
      onChanged()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Could not save the department.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="id-action">
      <label className="id-action-label">Department</label>
      <div className="cat-price-rule">
        <select value={value} onChange={(e) => setValue(e.target.value)} aria-label="Department">
          {!categories.includes(value) && <option value={value}>{value}</option>}
          {categories.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <button type="button" className="btn-primary" disabled={saving || value === categoryCode} onClick={save}>
          {saving ? 'Saving…' : 'Save department'}
        </button>
      </div>
      {error && <p className="stop">{error}</p>}
      {note && <p className="banner ok">{note}</p>}
    </div>
  )
}

/**
 * The Count action's delivery picker (design decision D5 of palletworks-nav): Item detail carries
 * no screen-level lot selector the way the deleted Catalog screen did, so this loads the open
 * deliveries itself and preselects the one open lot that still owes this product, when exactly one
 * does. The list itself is every open delivery, unfiltered — the same list Catalog's own picker
 * offered — because scoping the list itself to "owing" lots would need a new endpoint that
 * `ProductCountPane`'s already-clean empty-grid handling makes unnecessary.
 */
function CountEntry({
  productId,
  onCount,
}: {
  productId: string
  onCount: (lotId: string) => void
}) {
  const [deliveries, setDeliveries] = useState<DeliveryProgress[] | null>(null)
  const [owingLotId, setOwingLotId] = useState<string | null>(null)
  const [picked, setPicked] = useState('')

  useEffect(() => {
    let cancelled = false
    unpacking
      .deliveries()
      .then((rows) => {
        const open = rows.filter((d) => !d.closed)
        if (cancelled) return
        setDeliveries(open)
        return Promise.all(
          open.map((d) =>
            productCounting
              .lines(d.lotId, productId)
              .then((r) => (r.lines.length > 0 ? d.lotId : null))
              .catch(() => null),
          ),
        )
      })
      .then((owing) => {
        if (cancelled || !owing) return
        const owingIds = owing.filter((id): id is string => id != null)
        if (owingIds.length === 1) {
          setOwingLotId(owingIds[0])
          setPicked(owingIds[0])
        }
      })
      .catch(() => {
        if (!cancelled) setDeliveries([])
      })
    return () => {
      cancelled = true
    }
  }, [productId])

  return (
    <div className="id-action">
      <label className="id-action-label">Count</label>
      {deliveries === null ? (
        <p className="empty">Loading open deliveries…</p>
      ) : deliveries.length === 0 ? (
        <p className="empty">No open delivery to count into.</p>
      ) : (
        <div className="pcc-lot-row">
          <select
            id="id-count-lot-select"
            className="scan small pcc-lot-select"
            aria-label="Delivery"
            value={picked}
            onChange={(e) => setPicked(e.target.value)}
          >
            <option value="">Choose a delivery…</option>
            {deliveries.map((d) => (
              <option key={d.lotId} value={d.lotId}>
                {d.supplier} · {d.category}
                {d.lotId === owingLotId ? ' — owes this product' : ''}
              </option>
            ))}
          </select>
          <button type="button" className="btn-ghost" disabled={!picked} onClick={() => onCount(picked)}>
            Count
          </button>
        </div>
      )}
    </div>
  )
}
