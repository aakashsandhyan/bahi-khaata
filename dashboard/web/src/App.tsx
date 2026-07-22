import { useEffect, useMemo, useState } from 'react'
import { api, BackendError } from './api'
import type { PriceProposal, PriceableItem } from './types'
import { rupees } from './money'

/**
 * Pricing the shelf.
 *
 * The one thing this screen is for: seeing what goods cost against what they fetched online and
 * their printed MRP, and setting a price that earns a margin, stays under the online price, and
 * never breaches the MRP. A margin here is large — the goods were bought at a quarter of online
 * — so the work is a believable price, not a squeezed one.
 *
 * Bulk first. There are thousands of products, so a manager sets a margin for a whole category,
 * sees what it does to every line, prices the ones it fits in one act, and handles by hand only
 * the few it does not.
 */
export function App() {
  const [items, setItems] = useState<PriceableItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [category, setCategory] = useState<string | null>(null)

  const load = () => {
    setError(null)
    api
      .priceable('')
      .then(setItems)
      .catch((e) => setError(e instanceof BackendError ? e.message : 'Cannot reach the backend.'))
  }
  useEffect(load, [])

  const byCategory = useMemo(() => group(items ?? []), [items])
  const categories = Object.keys(byCategory).sort()

  return (
    <div className="page">
      <header>
        <h1>Pricing</h1>
        <p className="sub">
          What goods cost, what they fetch online, and what they may lawfully sell for. Set a
          price that earns, beats online, and stays under the MRP.
        </p>
      </header>

      {error && <div className="banner stop">{error}</div>}

      {items && items.length === 0 && (
        <div className="banner warn">
          Nothing is ready to price yet. A delivery must be unpacked and closed in the terminal
          before its cost is known — until then there is no margin to work from.
        </div>
      )}

      {categories.length > 0 && (
        <nav className="categories">
          {categories.map((c) => (
            <button
              key={c}
              className={c === category ? 'chip on' : 'chip'}
              onClick={() => setCategory(c)}
            >
              {c} <span className="count">{byCategory[c].length}</span>
            </button>
          ))}
        </nav>
      )}

      {category && byCategory[category] && (
        <CategoryPricing
          category={category}
          items={byCategory[category]}
          onChanged={load}
          onError={setError}
        />
      )}
    </div>
  )
}

function group(items: PriceableItem[]): Record<string, PriceableItem[]> {
  const out: Record<string, PriceableItem[]> = {}
  for (const item of items) (out[item.categoryCode] ??= []).push(item)
  return out
}

function CategoryPricing({
  category,
  items,
  onChanged,
  onError,
}: {
  category: string
  items: PriceableItem[]
  onChanged: () => void
  onError: (message: string) => void
}) {
  const [margin, setMargin] = useState(50)
  const [proposals, setProposals] = useState<Record<string, PriceProposal> | null>(null)
  const [busy, setBusy] = useState(false)

  const preview = () => {
    onError('')
    api
      .preview(category, margin)
      .then((list) => setProposals(Object.fromEntries(list.map((p) => [p.productId, p]))))
      .catch((e) => onError(e instanceof BackendError ? e.message : 'Preview failed.'))
  }

  const priceAll = async () => {
    setBusy(true)
    try {
      const result = await api.priceCategory(category, margin)
      onChanged()
      setProposals(null)
      alert(
        `Priced ${result?.priced}. Left alone: ${result?.skippedAlreadyPriced} already priced, ` +
          `${result?.skippedAboveMrp} that a ${margin}% margin would push above their MRP — those need you.`,
      )
    } catch (e) {
      onError(e instanceof BackendError ? e.message : 'Bulk pricing failed.')
    } finally {
      setBusy(false)
    }
  }

  const unpriced = items.filter((i) => i.currentPricePaise == null).length

  return (
    <section>
      <div className="rule">
        <label>
          Target margin{' '}
          <input
            type="number"
            min={0}
            max={95}
            value={margin}
            onChange={(e) => setMargin(Number(e.target.value))}
          />{' '}
          %
        </label>
        <button onClick={preview}>Preview</button>
        <button className="primary" disabled={busy || unpriced === 0} onClick={priceAll}>
          Price {unpriced} unpriced at {margin}%
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th className="name">Item</th>
            <th>Cost</th>
            <th>Online</th>
            <th>MRP</th>
            <th>Now</th>
            <th>At {margin}%</th>
            <th>Margin</th>
            <th>Checks</th>
            <th>Set a price</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <Row
              key={item.productId + item.condition}
              item={item}
              proposal={proposals?.[item.productId]}
              onChanged={onChanged}
              onError={onError}
            />
          ))}
        </tbody>
      </table>
    </section>
  )
}

function Row({
  item,
  proposal,
  onChanged,
  onError,
}: {
  item: PriceableItem
  proposal: PriceProposal | undefined
  onChanged: () => void
  onError: (message: string) => void
}) {
  const [entered, setEntered] = useState('')

  const setPrice = async (rupeeText: string) => {
    const paise = toPaise(rupeeText)
    if (paise == null) {
      onError('That is not a price. Type a rupee amount like 249 or 249.50.')
      return
    }
    try {
      await api.setPrice(item.productId, paise)
      setEntered('')
      onChanged()
    } catch (e) {
      onError(e instanceof BackendError ? e.message : 'Could not set the price.')
    }
  }

  return (
    <tr className={item.condition === 'DAMAGED' ? 'damaged' : ''}>
      <td className="name" title={item.name}>
        {item.name.length > 60 ? item.name.slice(0, 57) + '…' : item.name}
        {item.condition === 'DAMAGED' && <span className="tag">damaged</span>}
      </td>
      <td>{rupees(item.unitCostPaise)}</td>
      <td>{rupees(item.onlinePricePaise)}</td>
      <td>
        {rupees(item.mrpPaise)}
        {item.mrpIsEstimate && <span className="tag est">est</span>}
      </td>
      <td className={item.currentPricePaise ? 'priced' : ''}>{rupees(item.currentPricePaise)}</td>
      <td>{proposal ? rupees(proposal.pricePaise) : '—'}</td>
      <td>{proposal ? `${proposal.grossMarginPercent}%` : '—'}</td>
      <td className="checks">
        {proposal?.aboveMrp && <span className="flag stop">over MRP</span>}
        {proposal?.beatsOnline === false && <span className="flag warn">not under online</span>}
        {proposal && !proposal.aboveMrp && proposal.beatsOnline !== false && (
          <span className="flag ok">ok</span>
        )}
      </td>
      <td>
        <input
          className="price-in"
          placeholder="₹"
          value={entered}
          onChange={(e) => setEntered(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setPrice(entered)}
        />
      </td>
    </tr>
  )
}

function toPaise(text: string): number | null {
  const cleaned = text.trim().replace(/,/g, '').replace('₹', '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
  return Math.round(Number(cleaned) * 100)
}
