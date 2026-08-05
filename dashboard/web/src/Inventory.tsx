import { useEffect, useMemo, useState } from 'react'
import { inventory, BackendError } from './api'
import type { InventoryRow } from './types'
import { rupeesWhole } from './money'

// The four stock conditions, in the words the rest of the app already uses.
const CONDITION_LABEL: Record<string, string> = {
  GOOD: 'Good', DAMAGED: 'Damaged', NEEDS_WORK: 'Needs work', UNUSABLE: 'Unusable',
}
const CONDITION_TAG: Record<string, string> = {
  GOOD: 'tag-accent', DAMAGED: 'tag-accent-2', NEEDS_WORK: 'tag-outline', UNUSABLE: 'tag-neutral',
}

/**
 * The stock-centric view the shop lacked: what is on the floor, in which condition, where it
 * sits, and what it's worth — one row per product per stock condition, rolled up across whichever
 * lots back it (design decision D1 of palletworks-inventory).
 *
 * Everything below the fetch is client-side: filtering, the totals footer, and CSV export all
 * work off the rows already loaded in memory (design decisions D9/D10) — no further round trip
 * to the backend narrows or exports anything.
 */
const AGING_BUCKETS: { value: string; label: string; test: (days: number) => boolean }[] = [
  { value: '0-30', label: '0–30 days', test: (d) => d <= 30 },
  { value: '31-60', label: '31–60 days', test: (d) => d > 30 && d <= 60 },
  { value: '61-90', label: '61–90 days', test: (d) => d > 60 && d <= 90 },
  { value: '90+', label: '90+ days', test: (d) => d > 90 },
]

export function Inventory({ onOpenItem }: { onOpenItem: (productId: string) => void }) {
  const [rows, setRows] = useState<InventoryRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [condition, setCondition] = useState('')
  const [bin, setBin] = useState('')
  const [lot, setLot] = useState('')
  const [aging, setAging] = useState('')
  const [search, setSearch] = useState('')

  const load = () => {
    setError(null)
    inventory
      .rows()
      .then(setRows)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(load, [])

  const bins = useMemo(
    () => Array.from(new Set((rows ?? []).flatMap((r) => r.bins))).sort(),
    [rows],
  )
  const lots = useMemo(
    () => Array.from(new Set((rows ?? []).map((r) => r.lotLabel))).sort(),
    [rows],
  )

  const filtered = useMemo(() => {
    if (!rows) return []
    const bucket = AGING_BUCKETS.find((b) => b.value === aging)
    const term = search.trim().toLowerCase()
    return rows.filter((r) => {
      if (condition && r.condition !== condition) return false
      if (bin && !r.bins.includes(bin)) return false
      if (lot && r.lotLabel !== lot) return false
      if (bucket && !bucket.test(r.ageDays)) return false
      if (term && !r.productName.toLowerCase().includes(term)) return false
      return true
    })
  }, [rows, condition, bin, lot, aging, search])

  const totals = useMemo(() => {
    let units = 0
    let cost = 0
    let retail = 0
    for (const r of filtered) {
      units += r.onHandQuantity
      if (r.costBasisPaise != null) cost += r.costBasisPaise * r.onHandQuantity
      if (r.sellingPricePaise != null) retail += r.sellingPricePaise * r.onHandQuantity
    }
    return { units, cost, retail }
  }, [filtered])

  const exportCsv = () => {
    const header = ['Product', 'Condition', 'Lot', 'Bin', 'On hand', 'Cost basis', 'Price', 'Margin %', 'Age (days)']
    const lines = filtered.map((r) => [
      csvField(r.productName),
      r.condition,
      csvField(r.lotLabel),
      csvField(r.bins.join('; ')),
      String(r.onHandQuantity),
      r.costBasisPaise != null ? (r.costBasisPaise / 100).toFixed(2) : '',
      r.sellingPricePaise != null ? (r.sellingPricePaise / 100).toFixed(2) : '',
      r.marginPercent != null ? String(r.marginPercent) : '',
      String(r.ageDays),
    ])
    const csv = [header, ...lines].map((row) => row.join(',')).join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'inventory.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  if (error) {
    return (
      <div className="pad">
        <p className="banner stop">{error}</p>
      </div>
    )
  }
  if (!rows) return <div className="pad">Loading…</div>

  return (
    <div className="page inv">
      <header>
        <p className="sub">
          One row per product per condition, rolled up across its lots. Filter the floor down to
          what matters, then export exactly what's shown.
        </p>
      </header>

      <div className="inv-filters">
        <input
          className="scan small inv-search"
          placeholder="Search by product name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select value={condition} onChange={(e) => setCondition(e.target.value)} aria-label="Condition">
          <option value="">All conditions</option>
          {Object.entries(CONDITION_LABEL).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <select value={bin} onChange={(e) => setBin(e.target.value)} aria-label="Bin">
          <option value="">All bins</option>
          {bins.map((b) => (
            <option key={b} value={b}>{b}</option>
          ))}
        </select>
        <select value={lot} onChange={(e) => setLot(e.target.value)} aria-label="Lot">
          <option value="">All lots</option>
          {lots.map((l) => (
            <option key={l} value={l}>{l}</option>
          ))}
        </select>
        <select value={aging} onChange={(e) => setAging(e.target.value)} aria-label="Aging">
          <option value="">Any age</option>
          {AGING_BUCKETS.map((b) => (
            <option key={b.value} value={b.value}>{b.label}</option>
          ))}
        </select>
        <button type="button" className="btn-ghost" onClick={exportCsv}>
          Export CSV
        </button>
      </div>

      <div className="inv-table-wrap">
        <table className="inv-table">
          <thead>
            <tr>
              <th className="name">Product</th>
              <th className="name">Condition</th>
              <th className="name">Lot</th>
              <th className="name">Bin</th>
              <th>On hand</th>
              <th>Cost basis</th>
              <th>Price</th>
              <th>Margin</th>
              <th>Age</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 && (
              <tr>
                <td colSpan={9} className="inv-empty">Nothing matches the current filters.</td>
              </tr>
            )}
            {filtered.map((r) => (
              <tr
                key={`${r.productId}-${r.condition}`}
                className="inv-row"
                onClick={() => onOpenItem(r.productId)}
              >
                <td className="name"><span className="inv-name">{r.productName}</span></td>
                <td className="name">
                  <span className={`tag ${CONDITION_TAG[r.condition] ?? 'tag-neutral'}`}>
                    {CONDITION_LABEL[r.condition] ?? r.condition}
                  </span>
                </td>
                <td className="name">{r.lotLabel}</td>
                <td className="name">{r.bins.length ? r.bins.join(', ') : '—'}</td>
                <td>{r.onHandQuantity.toLocaleString('en-IN')}</td>
                <td>{rupeesWhole(r.costBasisPaise)}</td>
                <td>{rupeesWhole(r.sellingPricePaise)}</td>
                <td>{r.marginPercent != null ? `${r.marginPercent}%` : '—'}</td>
                <td>{r.ageDays}d</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="inv-totals">
              <td className="name" colSpan={4}>Filtered totals</td>
              <td>{totals.units.toLocaleString('en-IN')}</td>
              <td>{rupeesWhole(totals.cost)}</td>
              <td>{rupeesWhole(totals.retail)}</td>
              <td colSpan={2}></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  )
}

function csvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}
