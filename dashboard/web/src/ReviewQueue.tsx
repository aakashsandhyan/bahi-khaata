import { useEffect, useState } from 'react'
import { reviewQueue, shelfPricing, bulkPrint, printer, BackendError } from './api'
import type { LabelReviewEntry, CaptureSummary, ShelfLot } from './types'
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
      <h1>Review</h1>

      <AwaitingLabels />

      <h2 style={{ marginTop: 'var(--s4)' }}>Captures to finish</h2>
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

/**
 * The label review queue: one entry per product, the whole pricing command reviewed in one go — not
 * a row per sticker. The reviewer can edit each entry (name, category, price, MRP, copies), then
 * send them all to the print queue at once. Labels go through the spaced queue (two-up, paced), so a
 * big run cannot outrun the printer; the held-leftover banner flushes a lone sticker when done.
 */
function AwaitingLabels() {
  const [entries, setEntries] = useState<LabelReviewEntry[] | null>(null)
  const [held, setHeld] = useState(0)
  const [sending, setSending] = useState(false)
  const [flushing, setFlushing] = useState(false)
  const [message, setMessage] = useState('')
  const [editing, setEditing] = useState<string | null>(null)

  const refresh = () => {
    bulkPrint.reviewEntries().then((l) => setEntries(l ?? [])).catch(() => setEntries([]))
    printer.pendingCount().then((r) => setHeld(r?.count ?? 0)).catch(() => {})
  }
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 3000)
    return () => clearInterval(t)
  }, [])

  if (!entries) return null
  const totalLabels = entries.reduce((n, e) => n + e.copies, 0)
  const categories = Array.from(new Set(entries.map((e) => e.categoryCode))).sort()

  const sendAll = async () => {
    setSending(true)
    setMessage('')
    try {
      const r = await bulkPrint.sendReview()
      setMessage(
        `Sent ${r.labelsQueued} label${r.labelsQueued === 1 ? '' : 's'} for ${r.productsQueued} product${r.productsQueued === 1 ? '' : 's'} to the queue — printing now.`,
      )
    } catch (e) {
      setMessage(e instanceof BackendError ? e.message : 'Could not queue the labels.')
    } finally {
      setSending(false)
      refresh()
    }
  }

  return (
    <div style={{ border: '1px solid var(--line)', borderRadius: 'var(--r1)', padding: 'var(--s3)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)' }}>
        <h2 style={{ margin: 0, flex: 1 }}>Labels to print</h2>
        {entries.length > 0 && (
          <button className="btn-primary" disabled={sending} onClick={sendAll}>
            {sending ? 'Sending…' : `Send all to queue (${totalLabels} label${totalLabels === 1 ? '' : 's'})`}
          </button>
        )}
      </div>

      {message && <div style={{ marginTop: 'var(--s2)', fontSize: 13 }}>{message}</div>}

      {held > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', marginTop: 'var(--s2)',
          padding: 'var(--s2) var(--s3)', background: 'var(--line-soft)', borderRadius: 'var(--r1)' }}>
          <span style={{ flex: 1, fontSize: 14 }}><b>{held}</b> label{held > 1 ? 's' : ''} held, waiting to pair.</span>
          <button disabled={flushing} onClick={async () => {
            setFlushing(true)
            try { await printer.flush() } finally { setFlushing(false); refresh() }
          }}>
            {flushing ? 'Printing…' : 'Print held now'}
          </button>
        </div>
      )}

      {entries.length === 0 ? (
        <p style={{ color: 'var(--ink-faint)', marginBottom: 0 }}>Nothing waiting — every priced product has its labels.</p>
      ) : (
        <div style={{ marginTop: 'var(--s2)', maxHeight: 420, overflowY: 'auto' }}>
          {entries.map((e) => (
            <div key={e.jobId} style={{ padding: '6px 0', borderBottom: '1px solid var(--line-soft)' }}>
              <div style={{ display: 'flex', gap: 'var(--s2)', alignItems: 'center' }}>
                <span style={{ flex: 1 }}>{e.name}</span>
                <span style={{ color: 'var(--ink-faint)' }}>{rupees(e.sellingPricePaise)}</span>
                <span style={{ minWidth: 44, textAlign: 'right' }}>×{e.copies}</span>
                <button onClick={() => setEditing(editing === e.jobId ? null : e.jobId)}>
                  {editing === e.jobId ? 'Close' : 'Edit'}
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Reject labels for "${e.name}"? It leaves the queue; stock is untouched.`)) {
                      bulkPrint.rejectReview(e.jobId).then(refresh).catch(() => {})
                    }
                  }}
                >
                  Reject
                </button>
              </div>
              {editing === e.jobId && (
                <ReviewEditForm
                  entry={e}
                  categories={categories}
                  onDone={() => { setEditing(null); refresh() }}
                />
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Edit one review entry before its labels print — name, category, MRP, price, and how many copies.
 * The name/category/price/MRP are written back to the product so the till agrees; copies is how
 * many stickers this entry prints when sent. Stock is not touched here — that was set at pricing.
 */
function ReviewEditForm({
  entry,
  categories,
  onDone,
}: {
  entry: LabelReviewEntry
  categories: string[]
  onDone: () => void
}) {
  const [name, setName] = useState(entry.name)
  const [category, setCategory] = useState(entry.categoryCode)
  const [mrp, setMrp] = useState(entry.mrpPaise != null ? (entry.mrpPaise / 100).toString() : '')
  const [price, setPrice] = useState((entry.sellingPricePaise / 100).toString())
  const [copies, setCopies] = useState(entry.copies)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const paise = (s: string): number | null => {
    const n = Number(s)
    return Number.isFinite(n) && n > 0 ? Math.round(n * 100) : null
  }

  // The same floor the label and till use, so the reviewer sees the discount the sticker will show.
  const pricePaise = paise(price)
  const mrpPaise = mrp.trim() ? paise(mrp) : null
  const percentOff =
    mrpPaise != null && pricePaise != null && mrpPaise > pricePaise
      ? Math.floor(((mrpPaise - pricePaise) * 100) / mrpPaise)
      : null

  const options = Array.from(new Set([category, ...categories])).filter(Boolean)

  const save = async () => {
    setError(null)
    if (!name.trim()) return setError('Name cannot be empty.')
    if (!category) return setError('Choose a category.')
    if (pricePaise == null) return setError('Enter a selling price.')
    setSaving(true)
    try {
      await bulkPrint.editReview(entry.jobId, {
        name: name.trim(),
        categoryCode: category,
        sellingPricePaise: pricePaise,
        mrpPaise,
        copies,
      })
      onDone()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Save failed.')
    } finally {
      setSaving(false)
    }
  }

  const field: React.CSSProperties = { width: '100%', padding: 8 }
  return (
    <div style={{ marginTop: 'var(--s2)', padding: 'var(--s3)', background: 'var(--card)', border: '1px solid var(--line)', borderRadius: 'var(--r1)' }}>
      <label style={{ fontSize: 12, fontWeight: 600 }}>Name</label>
      <input value={name} onChange={(e) => setName(e.target.value)} style={field} />

      <div style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s2)' }}>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Category</label>
          <select value={category} onChange={(e) => setCategory(e.target.value)} style={field}>
            {options.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        <div style={{ width: 130 }}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Copies (on hand {entry.onHand})</label>
          <QtyInput value={copies} onChange={setCopies} min={0} style={field} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s2)' }}>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>MRP (optional)</label>
          <input value={mrp} onChange={(e) => setMrp(e.target.value)} placeholder="₹" style={field} />
        </div>
        <div style={{ flex: 1 }}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Selling price</label>
          <input value={price} onChange={(e) => setPrice(e.target.value)} placeholder="₹" style={field} />
        </div>
      </div>
      {percentOff != null && (
        <div style={{ fontSize: 12, color: 'var(--ink-faint)', marginTop: 4 }}>Label will show SAVE {percentOff}%.</div>
      )}

      {error && <p className="stop" style={{ marginTop: 'var(--s2)' }}>{error}</p>}
      <div style={{ display: 'flex', gap: 'var(--s2)', marginTop: 'var(--s2)' }}>
        <button className="btn-primary" style={{ flex: 1 }} disabled={saving} onClick={save}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
        <button style={{ flex: 1 }} onClick={onDone}>Cancel</button>
      </div>
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
