import { useEffect, useState } from 'react'
import { reviewQueue, shelfPricing, BackendError } from './api'
import type { CaptureSummary, ShelfLot } from './types'
import { rupees } from './money'
import { QtyInput } from './QtyInput'

/**
 * The review queue for captures made from a phone. Each is a pricing-free draft; a reviewer gives
 * it a lot, a category, and a price, then approves it — which creates the shelf product exactly as
 * the workbench would — or rejects it.
 */
export function ReviewQueue() {
  const [pending, setPending] = useState<CaptureSummary[] | null>(null)
  const [lots, setLots] = useState<ShelfLot[]>([])
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<string | null>(null)

  const load = () => {
    reviewQueue.pending().then(setPending)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(load, [])
  useEffect(() => { shelfPricing.lots().then(setLots).catch(() => {}) }, [])

  if (error && !pending) return <div className="pad"><p className="stop">{error}</p></div>
  if (!pending) return <div className="pad">Loading…</div>

  return (
    <div className="pad" style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Review queue</h1>
      {pending.length === 0 ? (
        <p>No captures waiting. The queue is clear.</p>
      ) : (
        pending.map((c) => (
          <div key={c.id} style={{ border: '1px solid var(--line)', borderRadius: 'var(--r1)', padding: 'var(--s3)', marginBottom: 'var(--s2)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600 }}>{c.name}</div>
                <div style={{ fontSize: 13, color: 'var(--ink-faint)' }}>
                  {c.mrpPaise ? `MRP ${rupees(c.mrpPaise)}` : 'no MRP'}{c.description ? ` · ${c.description}` : ''}
                </div>
              </div>
              {open !== c.id && <button className="btn-primary" onClick={() => setOpen(c.id)}>Review</button>}
              {open !== c.id && <button onClick={() => reviewQueue.reject(c.id).then(load)}>Reject</button>}
            </div>
            {open === c.id && (
              <ApproveForm capture={c} lots={lots} onDone={() => { setOpen(null); load() }} />
            )}
          </div>
        ))
      )}
    </div>
  )
}

const CONDITIONS = ['GOOD', 'DAMAGED']

function ApproveForm({ capture, lots, onDone }: { capture: CaptureSummary; lots: ShelfLot[]; onDone: () => void }) {
  const [lotId, setLotId] = useState(capture.lotId ?? '')
  const [categories, setCategories] = useState<string[]>([])
  const [category, setCategory] = useState('')
  const [condition, setCondition] = useState('GOOD')
  const [quantity, setQuantity] = useState(1)
  const [price, setPrice] = useState('')
  const [mrp, setMrp] = useState(capture.mrpPaise != null ? (capture.mrpPaise / 100).toString() : '')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!lotId) return
    shelfPricing.categoriesForLot(lotId).then(setCategories).catch(() => setCategories([]))
  }, [lotId])

  const paise = (s: string): number | null => {
    const n = Number(s)
    return Number.isFinite(n) && n > 0 ? Math.round(n * 100) : null
  }

  const approve = () => {
    setError(null)
    const pricePaise = paise(price)
    if (!lotId) return setError('Choose a lot.')
    if (!category) return setError('Choose a category.')
    if (pricePaise == null) return setError('Enter a selling price.')
    reviewQueue
      .approve(capture.id, {
        lotId, categoryCode: category, condition, quantity,
        sellingPricePaise: pricePaise, mrpPaise: mrp.trim() ? paise(mrp) : null,
      })
      .then(onDone)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Approve failed.'))
  }

  return (
    <div style={{ marginTop: 'var(--s2)', display: 'grid', gap: 'var(--s2)' }}>
      <select value={lotId} onChange={(e) => setLotId(e.target.value)} style={{ padding: 8 }}>
        <option value="">Choose a lot…</option>
        {lots.map((l) => <option key={l.lotId} value={l.lotId}>{l.supplier} · {l.receivedOn ?? '—'}</option>)}
      </select>
      <select value={category} onChange={(e) => setCategory(e.target.value)} style={{ padding: 8 }}>
        <option value="">Category…</option>
        {categories.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
      <div style={{ display: 'flex', gap: 'var(--s2)' }}>
        <select value={condition} onChange={(e) => setCondition(e.target.value)} style={{ padding: 8, flex: 1 }}>
          {CONDITIONS.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <QtyInput value={quantity} onChange={setQuantity} min={1} style={{ padding: 8, flex: 1 }} />
      </div>
      <input value={mrp} onChange={(e) => setMrp(e.target.value)} placeholder="MRP (optional)" style={{ padding: 8 }} />
      <input value={price} onChange={(e) => setPrice(e.target.value)} placeholder="Selling price" style={{ padding: 8 }} />
      {error && <p className="stop">{error}</p>}
      <div style={{ display: 'flex', gap: 'var(--s2)' }}>
        <button className="btn-primary" style={{ flex: 1 }} onClick={approve}>Approve → shelf</button>
        <button style={{ flex: 1 }} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}
