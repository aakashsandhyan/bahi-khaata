import { useEffect, useMemo, useState } from 'react'
import { checkout, inventory, registers, BackendError } from './api'
import type { CartView, InventoryRow, PaymentMethod, RegisterStateView, SaleView } from './types'
import { rupees } from './money'

// Which physical drawer this device is — remembered per device, like the operator name.
const REGISTER_KEY = 'till.register'

/**
 * The modern checkout, per the design artifact's Point of sale: scan bar and quick-picks grid on
 * the left, the live cart on the right — items with steppers, MRP total, customer saving, GST
 * included, To pay, Take payment. Register-gated: with no session open the screen offers opening
 * the drawer inline (selling-screens spec), never a dead end.
 *
 * Deliberately absent from the artifact's mock: the basket-margin line (cost figures are not an
 * operator's to see — the coming role gate hides them; showing margin at the till would leak
 * them to whoever is standing there), and Hold/Discount/Customers, which have no backend yet.
 */
export function CheckoutModern() {
  const [registerName, setRegisterName] = useState(
    () => localStorage.getItem(REGISTER_KEY) ?? 'Register 1')
  const [state, setState] = useState<RegisterStateView[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadRegisters = () => {
    registers.state().then(setState).catch(() => setError('Cannot reach the registers.'))
  }
  useEffect(loadRegisters, [])

  if (state === null) {
    return <div className="page">{error ? <div className="banner stop">{error}</div> : 'Loading…'}</div>
  }

  const mine = state.find((r) => r.registerName === registerName) ?? state[0]

  if (!mine?.open) {
    return (
      <OpenRegisterGate
        state={state}
        registerName={mine?.registerName ?? 'Register 1'}
        error={error}
        onPick={(name) => { localStorage.setItem(REGISTER_KEY, name); setRegisterName(name) }}
        onOpened={loadRegisters}
      />
    )
  }

  return <Pos register={mine} />
}

/** The inline register-open — the gate that is an on-ramp, not a wall. */
function OpenRegisterGate({ state, registerName, error, onPick, onOpened }: {
  state: RegisterStateView[]
  registerName: string
  error: string | null
  onPick: (name: string) => void
  onOpened: () => void
}) {
  const [operator, setOperator] = useState(() => localStorage.getItem('pricing.operator') ?? '')
  const [floatRupees, setFloatRupees] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)

  return (
    <div className="page" style={{ maxWidth: 480 }}>
      <h1>Checkout</h1>
      {(error || localError) && <div className="banner stop">{error || localError}</div>}
      <p><strong>{registerName}</strong> is not open — open it to start selling.</p>
      <div className="mode-toggle" style={{ marginBottom: 12 }}>
        {state.map((r) => (
          <button key={r.registerName} type="button"
            className={r.registerName === registerName ? 'on' : ''}
            onClick={() => onPick(r.registerName)}>
            {r.registerName}
          </button>
        ))}
      </div>
      <label style={{ display: 'block', marginBottom: 8 }}>
        Operator
        <input value={operator} onChange={(e) => setOperator(e.target.value)} placeholder="Who is on this drawer" />
      </label>
      <label style={{ display: 'block', marginBottom: 12 }}>
        Float counted in (₹)
        <input value={floatRupees} onChange={(e) => setFloatRupees(e.target.value)} inputMode="decimal" placeholder="2000" />
      </label>
      <button
        disabled={!operator.trim()}
        onClick={async () => {
          setLocalError(null)
          try {
            await registers.open(registerName, operator.trim(), Math.round(parseFloat(floatRupees || '0') * 100))
            onOpened()
          } catch (e) {
            setLocalError(e instanceof BackendError ? e.message : 'Cannot reach the registers.')
          }
        }}
      >
        Open {registerName} and start selling
      </button>
    </div>
  )
}

/** The selling surface itself, once the drawer is open. */
function Pos({ register }: { register: RegisterStateView }) {
  const [cart, setCart] = useState<CartView | null>(null)
  const [sale, setSale] = useState<SaleView | null>(null)
  const [rows, setRows] = useState<InventoryRow[]>([])
  const [chip, setChip] = useState<string>('ALL')
  // One field does both jobs, like the artifact: typing narrows the quick picks live, and Enter
  // tries the text as a scanned/keyed code. A scanner is just a fast keyboard ending in Enter,
  // so the hardware path and the search path are literally the same input.
  const [entry, setEntry] = useState('')
  const [paying, setPaying] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    checkout.open().then(setCart).catch(() => setError('Cannot reach the till.'))
    inventory.rows().then(setRows).catch(() => {})
  }, [])

  // Quick picks: what is actually sellable right now — priced, good, on hand.
  const sellable = useMemo(
    () => rows.filter((r) => r.sellingPricePaise != null && r.onHandQuantity > 0 && r.condition === 'GOOD'),
    [rows])
  const chips = useMemo(
    () => ['ALL', ...Array.from(new Set(sellable.map((r) => r.categoryCode))).sort()],
    [sellable])
  // Tokenized prefix match over an in-memory index — every query word must start some word of
  // the name or the category. The whole sellable catalogue is already in the browser (a few
  // thousand rows), so this answers per keystroke with no server round-trip; no search engine
  // needed at this scale (SQLite FTS5 is the upgrade path long before Elasticsearch would be).
  const indexed = useMemo(
    () => sellable.map((r) => ({
      row: r,
      tokens: `${r.productName} ${r.categoryCode}`.toLowerCase().split(/[^a-z0-9₹]+/).filter(Boolean),
    })),
    [sellable])
  const picks = useMemo(() => {
    const terms = entry.trim().toLowerCase().split(/\s+/).filter(Boolean)
    return indexed
      .filter(({ row }) => chip === 'ALL' || row.categoryCode === chip)
      .filter(({ tokens }) => terms.every((t) => tokens.some((tok) => tok.startsWith(t))))
      .slice(0, 30)
      .map(({ row }) => row)
  }, [indexed, chip, entry])

  const mutate = async (fn: () => Promise<CartView>) => {
    setError(null)
    try {
      setCart(await fn())
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    }
  }

  const onScan = (raw: string) => {
    const code = raw.trim()
    if (!code || !cart) return
    setEntry('')
    mutate(() => checkout.scan(cart.cartId, code))
  }

  const takePayment = async (method: PaymentMethod) => {
    if (!cart || busy) return
    setBusy(true)
    setError(null)
    try {
      const operator = localStorage.getItem('pricing.operator') || register.operatorName
      setSale(await checkout.complete(cart.cartId, method, operator, register.sessionId))
      setPaying(false)
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      setBusy(false)
    }
  }

  const newSale = () => {
    setSale(null)
    checkout.open().then(setCart).catch(() => setError('Cannot reach the till.'))
  }

  if (sale) {
    return (
      <div className="page" style={{ maxWidth: 520 }}>
        <h1>Sale {sale.billNoFormatted}</h1>
        {sale.printFailed
          ? <div className="banner warn">Recorded, but the bill did not print — fix the printer and reprint from Invoices.</div>
          : <div className="banner ok">Bill printed.</div>}
        <p style={{ fontSize: 22 }}>
          <strong>{rupees(sale.totalPaise)}</strong> · {sale.paymentMethod}
          {sale.savingPaise > 0 && <> · customer saved {rupees(sale.savingPaise)}</>}
        </p>
        <button className="pos-pay" onClick={newSale}>New sale</button>
      </div>
    )
  }

  const items = cart?.lines.reduce((n, l) => n + l.quantity, 0) ?? 0
  const mrpTotal = cart?.lines.reduce((n, l) => n + l.mrpPaise * l.quantity, 0) ?? 0

  return (
    <div className="pos">
      <section className="pos-left">
        {error && <div className="banner stop">{error}</div>}
        <div className="pos-scanrow">
          <input
            className="pos-scan"
            placeholder="Scan barcode or type SKU / product name…"
            value={entry}
            autoFocus
            onChange={(e) => setEntry(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onScan(entry)}
          />
        </div>
        <div className="mode-toggle pos-chips">
          {chips.map((c) => (
            <button key={c} type="button" className={chip === c ? 'on' : ''} onClick={() => setChip(c)}>
              {c === 'ALL' ? 'Quick picks' : c}
            </button>
          ))}
        </div>
        <div className="pos-grid">
          {picks.map((r) => (
            <button key={r.productId} type="button" className="pos-tile"
              onClick={() => cart && mutate(() => checkout.addProduct(cart.cartId, r.productId))}>
              <span className="pos-tile-cat">{r.categoryCode}</span>
              <span className="pos-tile-name">{r.productName}</span>
              <span className="pos-tile-foot">
                <strong>{rupees(r.sellingPricePaise!)}</strong>
                <span className="text-muted">
                  {r.bins[0] ? `${r.bins[0]} · ` : ''}{r.onHandQuantity} left
                </span>
              </span>
            </button>
          ))}
          {picks.length === 0 && <div className="text-muted" style={{ padding: 16 }}>Nothing sellable matches.</div>}
        </div>
      </section>

      <aside className="pos-cart">
        <div className="pos-cart-head">
          <h2 style={{ margin: 0 }}>Cart</h2>
          <button type="button" className="pos-clear"
            onClick={() => cart && mutate(() => checkout.clear(cart.cartId))}>
            Clear
          </button>
        </div>
        <div className="text-muted" style={{ marginBottom: 8 }}>
          Walk-in customer · {register.registerName} · {register.operatorName}
        </div>

        <div className="pos-lines">
          {(cart?.lines ?? []).map((l) => (
            <div key={l.lineId} className="pos-line">
              <div className="pos-line-who">
                <div>{l.name}</div>
                <div className="text-muted" style={{ fontSize: 12 }}>
                  {rupees(l.unitPricePaise)} each
                  {l.savingPaise > 0 && <> · saves {rupees(l.savingPaise)}</>}
                </div>
                <div className="pos-stepper">
                  <button onClick={() => cart && mutate(() => checkout.setQuantity(cart.cartId, l.lineId, l.quantity - 1))}>−</button>
                  <span>{l.quantity}</span>
                  <button onClick={() => cart && mutate(() => checkout.setQuantity(cart.cartId, l.lineId, l.quantity + 1))}>+</button>
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div><strong>{rupees(l.lineTotalPaise)}</strong></div>
                <button type="button" className="pos-clear"
                  onClick={() => cart && mutate(() => checkout.removeLine(cart.cartId, l.lineId))}>
                  Remove
                </button>
              </div>
            </div>
          ))}
          {items === 0 && <div className="text-muted" style={{ padding: '24px 0' }}>Scan or tap an item to begin.</div>}
        </div>

        <div className="pos-totals">
          <div><span>Items</span><span>{items}</span></div>
          <div><span>MRP total</span><span style={{ textDecoration: mrpTotal > (cart?.totalPaise ?? 0) ? 'line-through' : 'none' }}>{rupees(mrpTotal)}</span></div>
          <div><span>Customer saves</span><span className="pos-saves">{rupees(cart?.savingPaise ?? 0)}</span></div>
          {cart && !cart.taxIsPlaceholder && (
            <div><span>GST (included)</span><span>{rupees(cart.taxPaise)}</span></div>
          )}
          <div className="pos-topay"><span>To pay</span><span>{rupees(cart?.totalPaise ?? 0)}</span></div>
        </div>

        {!paying ? (
          <button className="pos-pay" disabled={items === 0 || busy} onClick={() => setPaying(true)}>
            Take payment
          </button>
        ) : (
          <div className="pos-methods">
            {(['CASH', 'UPI', 'CARD'] as PaymentMethod[]).map((m) => (
              <button key={m} disabled={busy} onClick={() => takePayment(m)}>{m}</button>
            ))}
            <button disabled={busy} onClick={() => setPaying(false)}>Back</button>
          </div>
        )}
      </aside>
    </div>
  )
}
