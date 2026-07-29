import { useEffect, useRef, useState } from 'react'
import { productCounting, BackendError } from './api'
import type { ProductBoxLine, RejectedEntry } from './types'

/**
 * Counting one product across every box of a single delivery at once, instead of opening each
 * box in Unpacking to find it there. A row per box that still owes this product units, a count
 * against each, one condition and one MRP for the whole submission, and a single submit.
 *
 * A box can move between the moment this grid is fetched and the moment it is submitted —
 * someone else counts it in Unpacking meanwhile, say. The backend checks each line's outstanding
 * against what this screen last saw and rejects any that no longer match, rather than silently
 * over- or under-counting it. A rejection is the normal outcome of that race, not a failure: it is
 * reported per box with its true count right now, so it can be re-entered and sent again.
 */
type Condition = 'GOOD' | 'DAMAGED'

interface Row extends ProductBoxLine {
  quantity: number
}

export function ProductCountPane({
  lotId,
  productId,
  onClose,
  onProgress,
  onDone,
}: {
  lotId: string
  productId: string
  // Back out without having counted anything.
  onClose: () => void
  // A count was recorded but some boxes were rejected — the pane stays open, so the caller just
  // refreshes whatever counts it shows elsewhere; no message of its own.
  onProgress: () => void
  // Every entered box counted cleanly — the caller refreshes and shows this confirmation, then
  // closes the pane.
  onDone: (message: string) => void
}) {
  const [productName, setProductName] = useState<string | null>(null)
  const [rows, setRows] = useState<Row[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [condition, setCondition] = useState<Condition>('GOOD')
  const [mrp, setMrp] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<{ text: string; tone: string } | null>(null)
  // Box trackings that came back rejected in the last submit, so their rows can be marked until
  // they are re-entered.
  const [rejectedIds, setRejectedIds] = useState<Set<string>>(new Set())
  // One submit per press: a quick second click while the request is in flight must not send the
  // same entries twice. The ref guards re-entry in the same tick; the state disables the button.
  const sent = useRef(false)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    productCounting
      .lines(lotId, productId)
      .then((data) => {
        setProductName(data.productName)
        setRows(data.lines.map((l) => ({ ...l, quantity: 0 })))
      })
      .catch((e) =>
        setLoadError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'),
      )
  }, [lotId, productId])

  const setQty = (lineId: string, quantity: number) => {
    setRows((prev) =>
      prev
        ? prev.map((r) =>
            r.lineId === lineId
              ? { ...r, quantity: Math.max(0, Math.min(r.outstanding, quantity)) }
              : r,
          )
        : prev,
    )
  }

  const submit = async () => {
    if (sent.current || !rows) return
    const entries = rows
      .filter((r) => r.quantity > 0)
      .map((r) => ({ lineId: r.lineId, quantity: r.quantity, outstandingSeen: r.outstanding }))
    if (entries.length === 0) {
      setError('Enter a count for at least one box.')
      return
    }
    let mrpPaise: number | null = null
    const cleaned = mrp.trim().replace(/,/g, '').replace('₹', '')
    if (cleaned) {
      if (!/^\d+(\.\d{1,2})?$/.test(cleaned) || Number(cleaned) <= 0) {
        setError('That is not a price. Type the number printed on the pack, like 249.')
        return
      }
      mrpPaise = Math.round(Number(cleaned) * 100)
    }
    setError(null)
    sent.current = true
    setSending(true)
    try {
      const result = await productCounting.count({
        productId,
        lotId,
        condition,
        mrpPaise,
        mrpIsEstimate: false,
        entries,
      })
      if (!result) return
      const rejectedById = new Map(result.rejected.map((r) => [r.lineId, r]))
      setRows((prev) =>
        (prev ?? []).map((r) => {
          const entry = entries.find((e) => e.lineId === r.lineId)
          if (!entry) return r
          const rejection: RejectedEntry | undefined = rejectedById.get(r.lineId)
          if (rejection) return { ...r, outstanding: rejection.nowOutstanding, quantity: 0 }
          return { ...r, outstanding: Math.max(0, r.outstanding - entry.quantity), quantity: 0 }
        }),
      )
      onProgress()
      const units = `${result.unitsCounted} unit${result.unitsCounted === 1 ? '' : 's'}`
      const boxes = `${result.linesCounted} box${result.linesCounted === 1 ? '' : 'es'}`
      if (result.rejected.length === 0) {
        onDone(`Counted ${units} across ${boxes}.`)
        return
      }
      setRejectedIds(new Set(result.rejected.map((r) => r.lineId)))
      setNotice({
        text:
          `Counted ${units} across ${boxes}. ${result.rejected.length} box` +
          `${result.rejected.length === 1 ? '' : 'es'} moved since this was opened — ` +
          'check below and re-enter.',
        tone: 'warn',
      })
      sent.current = false
      setSending(false)
    } catch (e) {
      sent.current = false
      setSending(false)
      setError(e instanceof BackendError ? e.message : 'Could not record the count.')
    }
  }

  if (loadError) {
    return (
      <div className="cat-detail pcc-pane">
        <button className="back" onClick={onClose}>
          ← Back
        </button>
        <div className="banner stop">{loadError}</div>
      </div>
    )
  }

  return (
    <div className="cat-detail pcc-pane">
      <button className="back" onClick={onClose}>
        ← Back
      </button>
      <h2>{productName ?? 'Loading…'}</h2>
      <p className="cat-detail-meta">Counting across this delivery&rsquo;s boxes</p>

      {notice && <div className={`banner ${notice.tone}`}>{notice.text}</div>}

      {rows === null && <p className="empty">Loading…</p>}
      {rows && rows.length === 0 && (
        <p className="empty">Nothing outstanding for this product in this delivery.</p>
      )}

      {rows && rows.length > 0 && (
        <>
          <div className="pcc-rows">
            {rows.map((r) => {
              const wasRejected = rejectedIds.has(r.lineId)
              return (
                <div
                  key={r.lineId}
                  className={`pcc-row${r.outstanding === 0 ? ' pcc-row-done' : ''}${
                    wasRejected ? ' pcc-row-rejected' : ''
                  }`}
                >
                  <div className="pcc-row-main">
                    <span className="pcc-box">{r.boxTracking}</span>
                    <span className="pcc-row-meta">
                      {wasRejected
                        ? `moved — now ${r.outstanding} to find, re-enter`
                        : `${r.outstanding} to find`}
                    </span>
                  </div>
                  <input
                    type="number"
                    className="pcc-qty-in"
                    min={0}
                    max={r.outstanding}
                    value={r.quantity}
                    disabled={r.outstanding === 0}
                    onChange={(e) => setQty(r.lineId, Math.floor(Number(e.target.value) || 0))}
                  />
                </div>
              )
            })}
          </div>

          <div className="cond-row pcc-cond-row">
            <button
              className={`cond on-good${condition === 'GOOD' ? ' sel' : ''}`}
              onClick={() => setCondition('GOOD')}
            >
              Fine
            </button>
            <button
              className={`cond on-damaged${condition === 'DAMAGED' ? ' sel' : ''}`}
              onClick={() => setCondition('DAMAGED')}
            >
              Damaged
            </button>
          </div>

          <input
            className="scan"
            placeholder="₹ MRP on the pack (blank = none printed)"
            value={mrp}
            onChange={(e) => {
              setMrp(e.target.value)
              setError(null)
            }}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />

          {error && <p className="mrp-hint stop">{error}</p>}

          <button className="btn-primary big pcc-submit" onClick={submit} disabled={sending}>
            {sending ? 'Saving…' : 'Submit'}
          </button>
        </>
      )}
    </div>
  )
}
