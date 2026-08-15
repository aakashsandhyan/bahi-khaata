import { useEffect, useState } from 'react'
import { bulkPrint, BackendError } from './api'
import type { AwaitingLabelProduct } from './types'
import { rupees } from './money'

/**
 * Printing labels in bulk for products priced but not yet labelled. Pick the ones to print; they
 * go out paired two to a row, so an odd count still leaves no blank sticker.
 */
export function BulkPrint() {
  const [items, setItems] = useState<AwaitingLabelProduct[] | null>(null)
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<string | null>(null)
  const [printing, setPrinting] = useState(false)

  const load = () => {
    setResult(null)
    bulkPrint.awaiting().then((list) => { setItems(list); setChosen(new Set()) })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(load, [])

  const toggle = (id: string) => {
    const next = new Set(chosen)
    next.has(id) ? next.delete(id) : next.add(id)
    setChosen(next)
  }

  const printChosen = async () => {
    setPrinting(true)
    setError(null)
    try {
      const r = await bulkPrint.print([...chosen])
      setResult(`Printed ${r.printed}${r.failed ? `, ${r.failed} failed` : ''}.`)
      load()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Print failed.')
    } finally {
      setPrinting(false)
    }
  }

  if (error && !items) return <div className="pad"><p className="stop">{error}</p></div>
  if (!items) return <div className="pad">Loading…</div>

  return (
    <div className="pad" style={{ maxWidth: 760, margin: '0 auto' }}>
      <h1>Bulk label print</h1>
      {result && <p className="good">{result}</p>}
      {error && <p className="stop">{error}</p>}

      {items.length === 0 ? (
        <p>Every priced product has a label. Nothing to print.</p>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 'var(--space-2)', margin: 'var(--space-2) 0' }}>
            <button onClick={() => setChosen(new Set(items.map((i) => i.productId)))}>Select all</button>
            <button onClick={() => setChosen(new Set())}>Clear</button>
            <button className="btn-primary" style={{ marginLeft: 'auto' }} disabled={!chosen.size || printing}
              onClick={printChosen}>
              {printing ? 'Printing…' : `Print ${chosen.size} label(s)`}
            </button>
          </div>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {items.map((i) => (
              <li key={i.productId}
                style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--color-neutral-200)' }}>
                <input type="checkbox" checked={chosen.has(i.productId)} onChange={() => toggle(i.productId)} />
                <span style={{ flex: 1 }}>{i.name}</span>
                <span style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--color-neutral-500)' }}>{i.barcode}</span>
                <span>{rupees(i.sellingPricePaise)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
