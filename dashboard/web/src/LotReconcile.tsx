import { useState } from 'react'
import { shelfPricing, BackendError } from './api'
import type { LotPhantomReport } from './types'

/**
 * Reconciling a lot at the end of pricing. Counted stock that was never priced — the orphaned
 * originals of items re-entered by hand — is phantom, and writing it off nets the double-count so
 * the system count matches the shelf. A deliberate step, taken once a lot's pricing is done.
 */
export function LotReconcile({ lotId }: { lotId: string }) {
  const [report, setReport] = useState<LotPhantomReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [wroteOff, setWroteOff] = useState<number | null>(null)

  const check = () => {
    setError(null)
    setWroteOff(null)
    shelfPricing.phantomReport(lotId).then(setReport).catch((e) =>
      setError(e instanceof BackendError ? e.message : 'Could not check.'))
  }

  const writeOff = () => {
    shelfPricing.writeOff(lotId).then((r) => { setWroteOff(r.quantityWrittenOff); setReport(null) })
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Write-off failed.'))
  }

  return (
    <details style={{ marginTop: 'var(--space-3)', borderTop: '1px solid var(--color-divider)', paddingTop: 'var(--space-2)' }}>
      <summary style={{ cursor: 'pointer', fontSize: 13, color: 'var(--color-neutral-500)' }}>
        Reconcile this lot (write off phantom stock)
      </summary>
      <div style={{ marginTop: 'var(--space-2)' }}>
        <button onClick={check}>Check for phantom stock</button>
        {error && <p className="stop">{error}</p>}
        {wroteOff != null && (
          <p className="good">Wrote off {wroteOff} unit(s) as shrinkage.</p>
        )}
        {report && (
          <div style={{ marginTop: 'var(--space-2)' }}>
            {report.totalPhantom === 0 ? (
              <p>No phantom stock — nothing to write off.</p>
            ) : (
              <>
                <p><b>{report.totalPhantom}</b> unit(s) counted but never priced:</p>
                <ul style={{ fontSize: 13 }}>
                  {report.lines.map((l) => (
                    <li key={l.batchId}>{l.name} — {l.quantity}</li>
                  ))}
                </ul>
                <button className="btn-primary" onClick={writeOff}>
                  Write off {report.totalPhantom} unit(s)
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </details>
  )
}
