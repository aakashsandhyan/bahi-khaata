import { useEffect, useState } from 'react'
import { customersApi, BackendError } from './api'
import type { CustomerDetail, CustomerList } from './types'
import { rupees } from './money'

/**
 * The Customers screen, per the design artifact: the stats strip (people on file, repeat share,
 * average repeat basket vs walk-in, lapsed 60+ days — the future offers audience), the list with
 * masked mobiles, and a per-customer detail with the full number and visit history. Every figure
 * arrives derived from customer × sale — nothing here is a stored counter.
 */
export function Customers() {
  const [data, setData] = useState<CustomerList | null>(null)
  const [detail, setDetail] = useState<CustomerDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  useEffect(() => {
    customersApi.list().then(setData).catch(() => setError('Cannot reach the customer record.'))
  }, [])

  const open = async (id: string) => {
    setError(null)
    try {
      setDetail(await customersApi.detail(id))
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the customer record.')
    }
  }

  if (detail) {
    return (
      <div className="page" style={{ maxWidth: 720 }}>
        <button className="pos-clear" onClick={() => setDetail(null)}>← All customers</button>
        <h1 style={{ marginTop: 8 }}>{detail.name}</h1>
        <div className="text-muted" style={{ marginBottom: 16 }}>
          {detail.mobile} · on file since {new Date(detail.since).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })}
        </div>
        <h2>Visits</h2>
        {detail.visits.length === 0 && <div className="text-muted">No purchases yet.</div>}
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <tbody>
            {detail.visits.map((v) => (
              <tr key={v.billNo} style={{ borderBottom: '1px solid var(--color-divider)' }}>
                <td style={{ padding: '8px 0', whiteSpace: 'nowrap' }}>
                  {new Date(v.at).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                </td>
                <td style={{ padding: '8px 12px' }}>
                  {v.what || v.billNoFormatted}
                  <span className="text-muted" style={{ fontSize: 12 }}> · {v.billNoFormatted}</span>
                </td>
                <td style={{ padding: '8px 0', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {rupees(v.amountPaise)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  const rows = (data?.customers ?? []).filter(
    (c) => !query.trim() || c.name.toLowerCase().includes(query.trim().toLowerCase()))
  const s = data?.stats

  return (
    <div className="page">
      <h1>Customers</h1>
      {error && <div className="banner stop">{error}</div>}

      {s && (
        <div className="cust-stats">
          <StatTile label="People on file" value={String(s.peopleOnFile)} />
          <StatTile label="Repeat share of revenue"
            value={s.repeatShareOfRevenuePercent != null ? `${s.repeatShareOfRevenuePercent}%` : '—'} />
          <StatTile label="Average repeat basket"
            value={s.averageRepeatBasketPaise != null ? rupees(s.averageRepeatBasketPaise) : '—'}
            sub={s.averageWalkInBasketPaise != null && s.averageRepeatBasketPaise != null && s.averageWalkInBasketPaise > 0
              ? `${Math.round((s.averageRepeatBasketPaise / s.averageWalkInBasketPaise - 1) * 100)}% vs a walk-in`
              : undefined} />
          <StatTile label="Lapsed 60 days+" value={String(s.lapsedSixtyDaysPlus)}
            sub="Worth a message when lots land" />
        </div>
      )}

      <input
        placeholder="Find a customer by name…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ margin: '12px 0', padding: '10px 12px', width: 'min(420px, 100%)' }}
      />

      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr className="text-muted" style={{ textAlign: 'left', fontSize: 12 }}>
            <th style={{ padding: '6px 0' }}>Customer</th>
            <th>Mobile</th>
            <th style={{ textAlign: 'right' }}>Visits</th>
            <th style={{ textAlign: 'right' }}>Spent</th>
            <th style={{ textAlign: 'right' }}>Avg basket</th>
            <th>Likes</th>
            <th>Last visit</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((c) => (
            <tr key={c.id} className="cust-row" onClick={() => open(c.id)}
              style={{ borderTop: '1px solid var(--color-divider)', cursor: 'pointer' }}>
              <td style={{ padding: '10px 0' }}>
                <div>{c.name}</div>
                <div className="text-muted" style={{ fontSize: 12 }}>{c.tag}</div>
              </td>
              <td>{c.mobileMasked}</td>
              <td style={{ textAlign: 'right' }}>{c.visits}</td>
              <td style={{ textAlign: 'right' }}>{rupees(c.spentPaise)}</td>
              <td style={{ textAlign: 'right' }}>{c.averagePaise != null ? rupees(c.averagePaise) : '—'}</td>
              <td>{c.likes || '—'}</td>
              <td>{c.lastVisitAt ? new Date(c.lastVisitAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : '—'}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={7} className="text-muted" style={{ padding: 16 }}>
              No customers yet — they arrive through the checkout's ask.
            </td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function StatTile({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="cust-stat">
      <div className="text-muted" style={{ fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700 }}>{value}</div>
      {sub && <div className="text-muted" style={{ fontSize: 12 }}>{sub}</div>}
    </div>
  )
}
