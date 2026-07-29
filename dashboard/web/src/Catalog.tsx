import { useEffect, useState } from 'react'
import { api, catalog, unpacking, BackendError } from './api'
import type { CatalogDetail, CatalogEntry, DeliveryProgress, StockCondition } from './types'
import { ProductCountPane } from './ProductCountPane'

/**
 * Browsing the product catalogue: every product the shop knows of, whether it has been laid
 * hands on yet, and whether it is priced.
 *
 * "On paper" is the gap this screen exists to close — a marketplace reference with nothing
 * counted or scanned onto it, so nobody could sell it if it walked in the door today. Opening a
 * product shows the stock it is actually held in and the codes that resolve to it, and lets a
 * price be set the same way Pricing does.
 *
 * A delivery filter narrows the whole screen to one open lot at a time. With one chosen, Count
 * opens a grid of that product's boxes in that lot — every box still owing it units, counted
 * together in one submit, rather than opening each box in Unpacking to find it there. Without a
 * lot chosen there is nothing to scope the count to, so Count stays a pointer back to picking one.
 * Mapping a new code onto a product is still an omission — it has no endpoint yet.
 */
const PAGE_SIZE = 25

const STATUS_TABS: { value: string; label: string }[] = [
  { value: 'on-paper', label: 'On paper' },
  { value: 'found', label: 'Found' },
  { value: 'all', label: 'All' },
]

// The shop's seven departments; a blank value means every department.
const CATEGORIES = [
  'KITCHEN',
  'WIRELESS',
  'FASHION',
  'FOOTWEAR',
  'HOME_ESSENTIALS',
  'PERSONAL_CARE',
  'GARDEN',
]

const STATE_LABEL: Record<StockCondition, string> = {
  GOOD: 'Ready',
  DAMAGED: 'Seconds',
  NEEDS_WORK: 'Needs work',
  UNUSABLE: 'Scrap',
}

export function Catalog() {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('on-paper')
  const [category, setCategory] = useState('')
  const [lot, setLot] = useState('')
  const [deliveries, setDeliveries] = useState<DeliveryProgress[]>([])
  const [entries, setEntries] = useState<CatalogEntry[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // A confirmation from product-centric counting — not an error, so it gets its own banner
  // rather than sharing the "stop" one.
  const [note, setNote] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [detail, setDetail] = useState<CatalogDetail | null>(null)

  const fail = (e: unknown) =>
    setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.')

  // Open deliveries only — a closed one has nothing left to count, so it does not belong in a
  // filter meant for narrowing what to look at while counting.
  useEffect(() => {
    unpacking
      .deliveries()
      .then((rows) => setDeliveries(rows.filter((d) => !d.closed)))
      .catch(() => {})
  }, [])

  const load = () => {
    setError(null)
    catalog
      .browse(query.trim(), status, category, 0, PAGE_SIZE, lot)
      .then((rows) => {
        setEntries(rows)
        setPage(0)
        setHasMore(rows.length === PAGE_SIZE)
      })
      .catch(fail)
  }
  useEffect(load, [query, status, category, lot])

  const loadMore = () => {
    const next = page + 1
    catalog
      .browse(query.trim(), status, category, next, PAGE_SIZE, lot)
      .then((rows) => {
        setEntries((prev) => [...prev, ...rows])
        setPage(next)
        setHasMore(rows.length === PAGE_SIZE)
      })
      .catch(fail)
  }

  const open = (productId: string) => {
    setSelected(productId)
    setDetail(null)
    setNote(null)
    catalog.detail(productId).then(setDetail).catch(fail)
  }

  const refresh = (productId: string) => {
    load()
    catalog.detail(productId).then(setDetail).catch(fail)
  }

  return (
    <div className="page">
      <header>
        <h1>Catalog</h1>
        <p className="sub">
          Every product the shop knows of, whether the goods have been laid hands on yet, and
          whether it carries a price. On paper is the gap to close — a marketplace reference with
          nothing counted or scanned onto it.
        </p>
      </header>

      {error && <div className="banner stop">{error}</div>}
      {note && <div className="banner ok">{note}</div>}

      <input
        className="scan small cat-search"
        placeholder="Find a product by name"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      <nav className="categories">
        {STATUS_TABS.map((t) => (
          <button
            key={t.value}
            className={t.value === status ? 'chip on' : 'chip'}
            onClick={() => setStatus(t.value)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <nav className="categories cat-depts">
        <button
          className={category === '' ? 'chip on' : 'chip'}
          onClick={() => setCategory('')}
        >
          All departments
        </button>
        {CATEGORIES.map((c) => (
          <button
            key={c}
            className={c === category ? 'chip on' : 'chip'}
            onClick={() => setCategory(c)}
          >
            {c}
          </button>
        ))}
      </nav>

      <div className="pcc-lot-row">
        <label htmlFor="cat-lot-select" className="pcc-lot-label">
          Delivery
        </label>
        <select
          id="cat-lot-select"
          className="scan small pcc-lot-select"
          value={lot}
          onChange={(e) => setLot(e.target.value)}
        >
          <option value="">All deliveries</option>
          {deliveries.map((d) => (
            <option key={d.lotId} value={d.lotId}>
              {d.supplier} · {d.category}
            </option>
          ))}
        </select>
      </div>

      <div className="cat-shell">
        <div className="cat-list">
          {entries.length === 0 && <p className="empty">Nothing matches.</p>}
          {entries.map((e) => (
            <button
              key={e.productId}
              className={`cat-row${selected === e.productId ? ' sel' : ''}`}
              onClick={() => open(e.productId)}
            >
              <span className="cat-row-main">
                <span className="cat-row-name">{e.name}</span>
                <span className="cat-row-meta">
                  {e.categoryCode}
                  {' · '}
                  <span className="cat-count" title="counted of expected across all its boxes">
                    {e.unitsCounted}/{e.unitsExpected} found
                  </span>
                  {e.unitsExpected - e.unitsCounted > 0 && (
                    <span className="cat-onpaper" title="units still on paper">
                      {' · '}
                      {e.unitsExpected - e.unitsCounted} on paper
                    </span>
                  )}
                </span>
              </span>
              <span className="cat-row-badges">
                <span className={`cat-badge ${e.status === 'FOUND' ? 'cat-good' : 'cat-warn'}`}>
                  {e.status === 'FOUND' ? 'Found' : 'On paper'}
                </span>
                <span className={`cat-badge ${e.priced ? 'cat-good' : 'cat-neutral'}`}>
                  {e.priced ? 'priced' : 'no price'}
                </span>
              </span>
            </button>
          ))}
          {hasMore && (
            <button className="btn-ghost cat-more" onClick={loadMore}>
              Load more
            </button>
          )}
        </div>

        {selected &&
          (detail ? (
            <CatalogDetailPanel
              productId={selected}
              detail={detail}
              lot={lot}
              onClose={() => {
                setSelected(null)
                setDetail(null)
              }}
              onPriced={() => refresh(selected)}
              onCountProgress={() => refresh(selected)}
              onCountDone={(message) => {
                refresh(selected)
                setNote(message)
              }}
              onError={setError}
            />
          ) : (
            <div className="cat-detail cat-loading">Loading…</div>
          ))}
      </div>
    </div>
  )
}

function CatalogDetailPanel({
  productId,
  detail,
  lot,
  onClose,
  onPriced,
  onCountProgress,
  onCountDone,
  onError,
}: {
  productId: string
  detail: CatalogDetail
  lot: string
  onClose: () => void
  onPriced: () => void
  onCountProgress: () => void
  onCountDone: (message: string) => void
  onError: (message: string) => void
}) {
  const [entered, setEntered] = useState('')
  const [countNote, setCountNote] = useState(false)
  const [counting, setCounting] = useState(false)

  const setPrice = async () => {
    const paise = toPaise(entered)
    if (paise == null) {
      onError('That is not a price. Type a rupee amount like 249 or 249.50.')
      return
    }
    try {
      await api.setPrice(productId, paise)
      setEntered('')
      onPriced()
    } catch (e) {
      onError(e instanceof BackendError ? e.message : 'Could not set the price.')
    }
  }

  if (counting && lot) {
    return (
      <ProductCountPane
        lotId={lot}
        productId={productId}
        onClose={() => setCounting(false)}
        onProgress={onCountProgress}
        onDone={(message) => {
          setCounting(false)
          onCountDone(message)
        }}
      />
    )
  }

  return (
    <div className="cat-detail">
      <button className="back" onClick={onClose}>
        ← Back
      </button>
      <h2>{detail.states.productName}</h2>
      <p className="cat-detail-meta">
        {detail.states.categoryCode}
        <span className={`cat-badge ${detail.status === 'FOUND' ? 'cat-good' : 'cat-warn'}`}>
          {detail.status === 'FOUND' ? 'Found' : 'On paper'}
        </span>
        <span className={`cat-badge ${detail.priced ? 'cat-good' : 'cat-neutral'}`}>
          {detail.priced ? 'priced' : 'no price'}
        </span>
      </p>

      <h3 className="cat-sub-head">Across its boxes</h3>
      <div className="cat-state-line">
        <span>Found</span>
        <span className="cat-row-meta">
          {detail.unitsCounted} of {detail.unitsExpected} expected
        </span>
      </div>
      {detail.unitsExpected - detail.unitsCounted > 0 && (
        <div className="cat-state-line">
          <span>Still on paper</span>
          <span className="cat-row-meta">{detail.unitsExpected - detail.unitsCounted} units</span>
        </div>
      )}

      <h3 className="cat-sub-head">Stock</h3>
      {detail.states.lines.length === 0 && <p className="empty">No stock held for this product.</p>}
      {detail.states.lines.map((line, idx) => (
        <div key={idx} className="cat-state-line">
          <span>
            {STATE_LABEL[line.condition]}
            {line.issueLabel ? ` · ${line.issueLabel}` : ''}
          </span>
          <span className="cat-row-meta">{line.quantity} units</span>
        </div>
      ))}

      <h3 className="cat-sub-head">Codes</h3>
      {detail.codes.length === 0 && <p className="empty">No code mapped to this product yet.</p>}
      {detail.codes.map((c) => (
        <div key={c.code} className="cat-code-line">
          <span className="cat-code">{c.code}</span>
          <span className="cat-row-meta">{c.origin.toLowerCase()}</span>
        </div>
      ))}

      <h3 className="cat-sub-head">Set a price</h3>
      <div className="cat-price-rule">
        <input
          className="price-in"
          placeholder="₹"
          value={entered}
          onChange={(e) => setEntered(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setPrice()}
        />
        <button className="btn-primary" onClick={setPrice}>
          Set price
        </button>
      </div>

      <h3 className="cat-sub-head">Count</h3>
      {lot ? (
        <button className="btn-ghost" onClick={() => setCounting(true)}>
          Count
        </button>
      ) : (
        <>
          <button className="btn-ghost" onClick={() => setCountNote(true)}>
            Count
          </button>
          {countNote && (
            <p className="cat-count-note">
              Choose a delivery above to count this product across its boxes.
            </p>
          )}
        </>
      )}
    </div>
  )
}

function toPaise(text: string): number | null {
  const cleaned = text.trim().replace(/,/g, '').replace('₹', '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
  return Math.round(Number(cleaned) * 100)
}
