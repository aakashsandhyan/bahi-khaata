import { useEffect, useState } from 'react'
import { shelfPricing, printer, BackendError } from './api'
import type { ScannedItem, ShelfLot, ShelfPricedProduct } from './types'
import { rupees } from './money'
import { LotReconcile } from './LotReconcile'

/**
 * Pricing the shelf, lot first.
 *
 * Pick a lot, then bring a product into it one of two ways: scan an item that is already counted
 * (its LSN/ASIN resolves it), or key one in by hand when nothing scans. Give it a category — the
 * price is suggested from the category's margin where the cost is known — confirm the MRP, set the
 * price, and save. On save the product is barcoded and offered for a label straight away.
 */
export function PricingWorkbench() {
  const [lots, setLots] = useState<ShelfLot[] | null>(null)
  const [lotId, setLotId] = useState<string>('')
  const [categories, setCategories] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)

  // The product being priced: either a scanned existing one, or a fresh hand-keyed draft.
  const [item, setItem] = useState<ScannedItem | null>(null)
  const [manual, setManual] = useState(false)

  useEffect(() => {
    shelfPricing
      .lots()
      .then(setLots)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }, [])

  useEffect(() => {
    if (!lotId) return
    setItem(null)
    setManual(false)
    shelfPricing.categoriesForLot(lotId).then(setCategories).catch(() => setCategories([]))
  }, [lotId])

  const scan = (code: string) => {
    setError(null)
    shelfPricing
      .scan(lotId, code)
      .then((found) => {
        if (found) {
          setItem(found)
          setManual(false)
        } else {
          setError(`"${code}" is not counted into this lot. Add it by hand instead.`)
        }
      })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Scan failed.'))
  }

  if (error && !lots) return <div className="pad"><p className="stop">{error}</p></div>
  if (!lots) return <div className="pad">Loading…</div>

  const done = () => {
    setItem(null)
    setManual(false)
  }

  return (
    <div className="pad" style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Pricing</h1>

      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginTop: 'var(--s3)' }}>
        Lot
      </label>
      <select
        value={lotId}
        onChange={(e) => setLotId(e.target.value)}
        style={{ width: '100%', padding: 8, fontSize: 14 }}
      >
        <option value="">Choose a lot to price…</option>
        {lots.map((l) => (
          <option key={l.lotId} value={l.lotId}>
            {l.supplier} · {l.receivedOn ?? '—'}
          </option>
        ))}
      </select>

      {lotId && !item && !manual && (
        <div style={{ marginTop: 'var(--s3)' }}>
          <ScanBox onScan={scan} />
          {error && <p className="stop" style={{ marginTop: 'var(--s2)' }}>{error}</p>}
          <button style={{ marginTop: 'var(--s2)' }} onClick={() => { setManual(true); setError(null) }}>
            + Add by hand (not counted / no code)
          </button>
          <LotReconcile lotId={lotId} />
        </div>
      )}

      {lotId && (item || manual) && (
        <PriceForm
          lotId={lotId}
          categories={categories}
          item={item}
          onCancel={done}
          onSaved={done}
        />
      )}
    </div>
  )
}

function ScanBox({ onScan }: { onScan: (code: string) => void }) {
  const [code, setCode] = useState('')
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        if (code.trim()) onScan(code.trim())
        setCode('')
      }}
    >
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600 }}>Scan an item</label>
      <input
        autoFocus
        value={code}
        onChange={(e) => setCode(e.target.value)}
        placeholder="Scan LSN / ASIN…"
        style={{ width: '100%', padding: 8, fontSize: 14 }}
      />
    </form>
  )
}

const CONDITIONS = ['GOOD', 'DAMAGED']

function PriceForm({
  lotId,
  categories,
  item,
  onCancel,
  onSaved,
}: {
  lotId: string
  categories: string[]
  item: ScannedItem | null
  onCancel: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(item?.name ?? '')
  const [category, setCategory] = useState(item?.categoryCode ?? '')
  const [condition, setCondition] = useState('GOOD')
  const [quantity, setQuantity] = useState(1)
  const [mrp, setMrp] = useState<string>(item?.mrpPaise != null ? (item.mrpPaise / 100).toString() : '')
  const [price, setPrice] = useState<string>('')
  const [suggested, setSuggested] = useState<number | null>(null)
  const [saved, setSaved] = useState<ShelfPricedProduct | null>(null)
  const [error, setError] = useState<string | null>(null)

  const costed = item?.costed ?? false
  const unitCost = item?.unitCostPaise ?? null

  // Suggest a price once a category is chosen and the cost is known. Uncosted stock is hand-priced.
  useEffect(() => {
    setSuggested(null)
    if (!category || !costed || unitCost == null) return
    shelfPricing
      .suggest(unitCost, category)
      .then((s) => {
        setSuggested(s.suggestedPricePaise)
        if (!price) setPrice((s.suggestedPricePaise / 100).toString())
      })
      .catch(() => {})
  }, [category, costed, unitCost])

  const paise = (rupeeStr: string): number | null => {
    const n = Number(rupeeStr)
    return Number.isFinite(n) && n > 0 ? Math.round(n * 100) : null
  }

  const save = () => {
    setError(null)
    const pricePaise = paise(price)
    if (!category) return setError('Choose a category.')
    if (pricePaise == null) return setError('Enter a selling price.')
    const mrpPaise = mrp.trim() ? paise(mrp) : null
    const req = item
      ? shelfPricing.saveExisting({
          productId: item.productId,
          batchId: item.batchId,
          categoryCode: category,
          sellingPricePaise: pricePaise,
          mrpPaise,
        })
      : shelfPricing.saveManual({
          lotId,
          name: name.trim(),
          categoryCode: category,
          condition,
          quantity,
          sellingPricePaise: pricePaise,
          mrpPaise,
        })
    if (!item && !name.trim()) return setError('Enter a product name.')
    req.then(setSaved).catch((e) => setError(e instanceof BackendError ? e.message : 'Save failed.'))
  }

  if (saved) return <SavedCard product={saved} onDone={onSaved} />

  return (
    <div style={{ marginTop: 'var(--s3)', padding: 'var(--s3)', border: '1px solid var(--line)', borderRadius: 'var(--r1)' }}>
      <h2 style={{ marginTop: 0 }}>{item ? 'Price this item' : 'New product'}</h2>

      <Field label="Name">
        <input value={name} onChange={(e) => setName(e.target.value)} disabled={!!item}
          style={{ width: '100%', padding: 8 }} />
      </Field>

      <Field label="Category">
        <select value={category} onChange={(e) => setCategory(e.target.value)} style={{ width: '100%', padding: 8 }}>
          <option value="">Choose…</option>
          {(categories.length ? categories : [category].filter(Boolean)).map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </Field>

      {!item && (
        <div style={{ display: 'flex', gap: 'var(--s2)' }}>
          <Field label="Condition">
            <select value={condition} onChange={(e) => setCondition(e.target.value)} style={{ width: '100%', padding: 8 }}>
              {CONDITIONS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </Field>
          <Field label="Quantity">
            <input type="number" min={1} value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              style={{ width: '100%', padding: 8 }} />
          </Field>
        </div>
      )}

      <Field label="MRP (optional)">
        <input value={mrp} onChange={(e) => setMrp(e.target.value)} placeholder="₹ printed on the pack"
          style={{ width: '100%', padding: 8 }} />
      </Field>

      <Field label="Selling price">
        <input value={price} onChange={(e) => setPrice(e.target.value)}
          placeholder={costed ? '₹' : '₹ (no suggestion — cost not known)'}
          style={{ width: '100%', padding: 8 }} />
        {costed && unitCost != null && (
          <div style={{ fontSize: 12, color: 'var(--ink-faint)', marginTop: 4 }}>
            Cost {rupees(unitCost)}{suggested != null ? ` · suggested ${rupees(suggested)}` : ''}
          </div>
        )}
        {!costed && (
          <div style={{ fontSize: 12, color: 'var(--ink-faint)', marginTop: 4 }}>
            This lot is not costed yet — price it by hand.
          </div>
        )}
      </Field>

      {error && <p className="stop">{error}</p>}
      <div style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s2)' }}>
        <button className="btn-primary" style={{ flex: 1 }} onClick={save}>Save product</button>
        <button style={{ flex: 1 }} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function SavedCard({ product, onDone }: { product: ShelfPricedProduct; onDone: () => void }) {
  const [state, setState] = useState<'ask' | 'printing' | 'done' | 'failed'>('ask')
  const [message, setMessage] = useState('')

  const print = async () => {
    setState('printing')
    try {
      const job = await printer.queueLabel({
        barcode: product.barcode,
        productName: product.name,
        sellingPricePaise: product.sellingPricePaise,
        mrpPaise: product.mrpPaise,
        copies: 1,
        productId: product.productId,
      })
      if (!job) throw new Error('Could not queue the label.')
      const poll = setInterval(async () => {
        const s = await printer.getJobStatus(job.jobId)
        if (!s) return
        if (s.status === 'done') { clearInterval(poll); setState('done'); setMessage('Label printed.') }
        else if (s.status === 'failed') { clearInterval(poll); setState('failed'); setMessage(s.error || 'Print failed.') }
      }, 500)
    } catch (e) {
      setState('failed')
      setMessage(e instanceof BackendError ? e.message : 'Could not queue the label.')
    }
  }

  return (
    <div style={{ marginTop: 'var(--s3)', padding: 'var(--s3)', background: 'var(--good-tint)', borderRadius: 'var(--r1)' }}>
      <div style={{ fontWeight: 600 }}>✓ {product.name} saved · {product.barcode}</div>
      <div style={{ fontSize: 14, marginTop: 4 }}>
        {rupees(product.sellingPricePaise)}{product.mrpPaise ? ` · MRP ${rupees(product.mrpPaise)}` : ''}
      </div>
      {message && <div style={{ marginTop: 'var(--s2)' }}>{message}</div>}
      <div style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s2)' }}>
        {state === 'ask' && <button className="btn-primary" style={{ flex: 1 }} onClick={print}>Print label</button>}
        {state === 'printing' && <button disabled style={{ flex: 1, opacity: 0.6 }}>Printing…</button>}
        <button style={{ flex: 1 }} onClick={onDone}>{state === 'ask' ? 'Skip (print later)' : 'Next product'}</button>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 'var(--s2)', flex: 1 }}>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  )
}
