import { useEffect, useState } from 'react'
import { gst, BackendError } from './api'
import type { GstRateRow } from './types'

/**
 * Admin: the GST rate per sub-category (the rate-group a product belongs to). Rates are placeholders
 * until the CA confirms them — set the real percentages here before go-live; a change takes effect
 * on the next sale and never moves a past bill.
 */
export function GstRates() {
  const [rows, setRows] = useState<GstRateRow[] | null>(null)
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  useEffect(() => {
    load()
  }, [])

  const load = async () => {
    try {
      const data = await gst.rates()
      setRows(data)
      setDraft(Object.fromEntries(data.map((r) => [r.code, percentOf(r.basisPoints)])))
    } catch (err) {
      setMessage({ text: reason(err), tone: 'stop' })
    }
  }

  const save = async (code: string) => {
    const percent = Number(draft[code])
    if (!Number.isFinite(percent) || percent < 0 || percent > 100) {
      setMessage({ text: 'Enter a percent between 0 and 100', tone: 'stop' })
      return
    }
    try {
      await gst.setRate(code, Math.round(percent * 100)) // percent → basis points
      setMessage({ text: `✓ ${code} set to ${percent}%`, tone: 'ok' })
      await load()
    } catch (err) {
      setMessage({ text: reason(err), tone: 'stop' })
    }
  }

  if (rows === null) return <div className="pad">Loading GST rates…</div>

  const byCategory = groupByCategory(rows)

  return (
    <div className="pad" style={{ maxWidth: 720, margin: '0 auto' }}>
      <h1>GST rates</h1>
      <p style={{ color: 'var(--ink-faint)', fontSize: 14 }}>
        The rate per sub-category, extracted inclusively at the till. Placeholders until your CA
        confirms them — set the real figures before opening. A change applies to new sales only.
      </p>
      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {Object.entries(byCategory).map(([category, catRows]) => (
        <div key={category} style={{ marginTop: 'var(--s4)' }}>
          <h2 style={{ fontSize: 15, color: 'var(--ink-soft)' }}>{category}</h2>
          {catRows.map((r) => (
            <div
              key={r.code}
              style={{ display: 'flex', alignItems: 'center', gap: 'var(--s2)', padding: '6px 0', borderBottom: '1px solid var(--line-soft)' }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, fontSize: 14 }}>{r.name}</div>
                <div style={{ fontSize: 12, color: 'var(--ink-faint)' }}>{r.code}</div>
              </div>
              <input
                type="number"
                min={0}
                max={100}
                step="0.25"
                value={draft[r.code] ?? ''}
                onChange={(e) => setDraft({ ...draft, [r.code]: e.target.value })}
                style={{ width: 90, padding: 6, textAlign: 'right' }}
              />
              <span style={{ width: 16, color: 'var(--ink-faint)' }}>%</span>
              <button onClick={() => save(r.code)} style={{ padding: '6px 12px' }}>Save</button>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function percentOf(basisPoints: number | null): string {
  return basisPoints == null ? '' : String(basisPoints / 100)
}

function groupByCategory(rows: GstRateRow[]): Record<string, GstRateRow[]> {
  const out: Record<string, GstRateRow[]> = {}
  for (const r of rows) (out[r.category] ??= []).push(r)
  return out
}

function reason(err: unknown): string {
  return err instanceof BackendError ? err.message : 'Cannot reach the system.'
}
