import { useEffect, useMemo, useState } from 'react'
import { catalog, inventory, shelfPricing, BackendError } from './api'
import type { CatalogEntry, InventoryRow } from './types'
import { rupeesWhole } from './money'

// The four stock conditions, in the words the rest of the app already uses.
const CONDITION_LABEL: Record<string, string> = {
  GOOD: 'Good', DAMAGED: 'Damaged', NEEDS_WORK: 'Needs work', UNUSABLE: 'Unusable',
}
const CONDITION_TAG: Record<string, string> = {
  GOOD: 'tag-accent', DAMAGED: 'tag-accent-2', NEEDS_WORK: 'tag-outline', UNUSABLE: 'tag-neutral',
}

/**
 * The stock-centric view the shop lacked, now folded together with the product-finding jobs the
 * deleted Catalog screen used to do (design decisions D2/D3/D9 of palletworks-nav).
 *
 * A scope control swaps both the dataset and the column set, not a row filter over one table (D3):
 * On floor is the original stock rollup — one row per product per stock condition, `GET
 * /api/inventory` — with its condition/bin/lot/aging filters, its totals footer, and CSV export
 * all still client-side over the loaded rows. On paper and All ride `catalog.browse` instead (D2)
 * — the same endpoint the deleted Catalog screen browsed — and render the catalog's own columns
 * (status, priced, department, counted-of-expected) rather than fabricating stock cells an
 * uncounted product does not have. Free-text search and the department filter work in every scope;
 * condition/bin/lot/aging and the totals footer are On floor only, because they read or sum
 * stock-only fields.
 */
const PAGE_SIZE = 25

const AGING_BUCKETS: { value: string; label: string; test: (days: number) => boolean }[] = [
  { value: '0-30', label: '0–30 days', test: (d) => d <= 30 },
  { value: '31-60', label: '31–60 days', test: (d) => d > 30 && d <= 60 },
  { value: '61-90', label: '61–90 days', test: (d) => d > 60 && d <= 90 },
  { value: '90+', label: '90+ days', test: (d) => d > 90 },
]

type Scope = 'floor' | 'paper' | 'all'

const SCOPES: { value: Scope; label: string }[] = [
  { value: 'floor', label: 'On floor' },
  { value: 'paper', label: 'On paper' },
  { value: 'all', label: 'All' },
]

export function Inventory({ onOpenItem }: { onOpenItem: (productId: string) => void }) {
  const [scope, setScope] = useState<Scope>('floor')
  const [search, setSearch] = useState('')
  const [department, setDepartment] = useState('')
  const [categories, setCategories] = useState<string[]>([])

  // --- On floor: the stock rollup, unchanged from before this change. ---
  const [rows, setRows] = useState<InventoryRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [condition, setCondition] = useState('')
  const [bin, setBin] = useState('')
  const [lot, setLot] = useState('')
  const [aging, setAging] = useState('')

  // --- On paper / All: the catalog listing (D2), paged the same way Catalog.tsx paged it. ---
  const [entries, setEntries] = useState<CatalogEntry[] | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)

  useEffect(() => {
    shelfPricing.categories().then(setCategories).catch(() => setCategories([]))
  }, [])

  const loadFloor = () => {
    setError(null)
    inventory
      .rows()
      .then(setRows)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(loadFloor, [])

  const catalogStatus = scope === 'paper' ? 'on-paper' : 'all'
  const loadCatalog = () => {
    if (scope === 'floor') return
    setCatalogError(null)
    catalog
      .browse(search.trim(), catalogStatus, department, 0, PAGE_SIZE)
      .then((data) => {
        setEntries(data)
        setPage(0)
        setHasMore(data.length === PAGE_SIZE)
      })
      .catch((e) => setCatalogError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(loadCatalog, [scope, search, department])

  const loadMoreCatalog = () => {
    const next = page + 1
    catalog
      .browse(search.trim(), catalogStatus, department, next, PAGE_SIZE)
      .then((data) => {
        setEntries((prev) => [...(prev ?? []), ...data])
        setPage(next)
        setHasMore(data.length === PAGE_SIZE)
      })
      .catch((e) => setCatalogError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }

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
      if (department && r.categoryCode !== department) return false
      if (term && !r.productName.toLowerCase().includes(term)) return false
      return true
    })
  }, [rows, condition, bin, lot, aging, department, search])

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

  return (
    <div className="page inv">
      <header>
        <p className="sub">
          On floor is one row per product per condition, rolled up across its lots. On paper is
          the gap to close — a marketplace reference with nothing counted or scanned onto it yet.
          All lists both together.
        </p>
      </header>

      <div className="seg">
        {SCOPES.map((s) => (
          <label key={s.value} className="seg-opt">
            <input
              type="radio"
              name="inv-scope"
              value={s.value}
              checked={scope === s.value}
              onChange={() => setScope(s.value)}
            />
            {s.label}
          </label>
        ))}
      </div>

      <div className="inv-filters">
        <input
          className="scan small inv-search"
          placeholder="Search by product name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {scope === 'floor' && (
          <>
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
          </>
        )}
      </div>

      <nav className="categories inv-depts">
        <button
          type="button"
          className={department === '' ? 'chip on' : 'chip'}
          onClick={() => setDepartment('')}
        >
          All departments
        </button>
        {categories.map((c) => (
          <button
            key={c}
            type="button"
            className={c === department ? 'chip on' : 'chip'}
            onClick={() => setDepartment(c)}
          >
            {c}
          </button>
        ))}
      </nav>

      {scope === 'floor' ? (
        error ? (
          <p className="banner stop">{error}</p>
        ) : !rows ? (
          <div className="pad">Loading…</div>
        ) : (
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
        )
      ) : catalogError ? (
        <p className="banner stop">{catalogError}</p>
      ) : !entries ? (
        <div className="pad">Loading…</div>
      ) : (
        <div className="inv-table-wrap">
          <table className="inv-table">
            <thead>
              <tr>
                <th className="name">Product</th>
                <th className="name">Department</th>
                <th className="name">Status</th>
                <th className="name">Priced</th>
                <th>Found</th>
              </tr>
            </thead>
            <tbody>
              {entries.length === 0 && (
                <tr>
                  <td colSpan={5} className="inv-empty">Nothing matches the current filters.</td>
                </tr>
              )}
              {entries.map((e) => (
                <tr
                  key={e.productId}
                  className="inv-row"
                  onClick={() => onOpenItem(e.productId)}
                >
                  <td className="name"><span className="inv-name">{e.name}</span></td>
                  <td className="name"><span className="tag tag-neutral">{e.categoryCode}</span></td>
                  <td className="name">
                    <span className={`cat-badge ${e.status === 'FOUND' ? 'cat-good' : 'cat-warn'}`}>
                      {e.status === 'FOUND' ? 'Found' : 'On paper'}
                    </span>
                  </td>
                  <td className="name">
                    <span className={`cat-badge ${e.priced ? 'cat-good' : 'cat-neutral'}`}>
                      {e.priced ? 'priced' : 'no price'}
                    </span>
                  </td>
                  <td>{e.unitsCounted}/{e.unitsExpected}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {hasMore && (
            <button type="button" className="btn-ghost cat-more" onClick={loadMoreCatalog}>
              Load more
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function csvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}
