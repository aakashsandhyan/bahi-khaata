import { useState } from 'react'
import { printer, BackendError } from './api'
import type { AwaitingLabelProduct } from './types'
import { rupees } from './money'
import { QtyInput } from './QtyInput'

/**
 * Reprint a label by barcode.
 *
 * Scan or type any of a product's barcodes — its shelf BBZ, or the original LSN/ASIN — to pull up
 * the priced product and print more labels for it. This touches no stock and runs no pricing flow;
 * it is also the one place in the app that searches by barcode. Labels go through the same
 * self-contained queue as pricing, so they pair two-up and a lone one is held.
 */
export function Reprint() {
  const [found, setFound] = useState<AwaitingLabelProduct | null>(null)
  const [error, setError] = useState<string | null>(null)

  const lookup = (code: string) => {
    setError(null)
    setFound(null)
    printer
      .labelFor(code)
      .then((p) => {
        if (p) setFound(p)
        else setError(`Nothing found for "${code}".`)
      })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Lookup failed.'))
  }

  return (
    <div className="pad" style={{ maxWidth: 620, margin: '0 auto' }}>
      <h1>Reprint a label</h1>
      <p style={{ fontSize: 14, color: 'var(--color-neutral-500)', marginTop: 0 }}>
        Scan any barcode — the shelf BBZ or the original code. No stock is touched.
      </p>

      <ScanBox onScan={lookup} />
      {error && <p className="stop" style={{ marginTop: 'var(--space-2)' }}>{error}</p>}

      {found && <ReprintCard key={found.barcode} product={found} onDone={() => setFound(null)} />}
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
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600 }}>Barcode</label>
      <input
        autoFocus
        value={code}
        onChange={(e) => setCode(e.target.value)}
        placeholder="Scan or type a barcode…"
        style={{ width: '100%', padding: 8, fontSize: 14 }}
      />
    </form>
  )
}

function ReprintCard({ product, onDone }: { product: AwaitingLabelProduct; onDone: () => void }) {
  const [copies, setCopies] = useState(1)
  const [state, setState] = useState<'ask' | 'queued' | 'failed'>('ask')
  const [message, setMessage] = useState('')

  const queue = async () => {
    try {
      const job = await printer.queueLabel({
        barcode: product.barcode,
        productName: product.name,
        sellingPricePaise: product.sellingPricePaise,
        mrpPaise: product.mrpPaise,
        copies,
        productId: product.productId,
      })
      if (!job) throw new Error('Could not queue the label.')
      setState('queued')
      // Held, not flushed: labels print two to a row, so a lone one waits to pair with the next.
      setMessage(`${copies} label${copies > 1 ? 's' : ''} queued — printed two to a row; a lone one waits to pair (or use “Print pending now” on Pricing).`)
    } catch (e) {
      setState('failed')
      setMessage(e instanceof BackendError ? e.message : 'Could not queue the label.')
    }
  }

  return (
    <div style={{ marginTop: 'var(--space-3)', padding: 'var(--space-3)', border: '1px solid var(--color-divider)', borderRadius: 'var(--radius-md)' }}>
      <div style={{ fontWeight: 600 }}>{product.name}</div>
      <div style={{ fontSize: 14, marginTop: 4 }}>
        {product.barcode} · {rupees(product.sellingPricePaise)}
        {product.mrpPaise ? ` · MRP ${rupees(product.mrpPaise)}` : ''}
      </div>

      <div style={{ marginTop: 'var(--space-3)' }}>
        <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>Labels to print</label>
        <QtyInput value={copies} onChange={setCopies} min={1} max={100} style={{ width: '100%', padding: 8 }} />
      </div>

      {message && <div style={{ marginTop: 'var(--space-2)', fontSize: 13 }}>{message}</div>}

      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
        {state === 'ask' && (
          <button className="btn-primary" style={{ flex: 1 }} onClick={queue}>
            Queue {copies} label{copies > 1 ? 's' : ''}
          </button>
        )}
        <button className={state === 'ask' ? '' : 'btn-primary'} style={{ flex: 1 }} onClick={onDone}>
          {state === 'ask' ? 'Cancel' : 'Reprint another'}
        </button>
      </div>
    </div>
  )
}
