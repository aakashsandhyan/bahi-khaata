import { useEffect, useMemo, useState } from 'react'
import { checkout, customersApi, inventory, receiving, registers, BackendError } from './api'
import type { CartSummary, CartView, CustomerView, InventoryRow, LotSummary, PaymentMethod, RegisterStateView, SaleView } from './types'
import { rupees } from './money'

// Which physical drawer this device is — remembered per device, like the operator name.
const REGISTER_KEY = 'till.register'
// The device's cart pointer — the only client state; the cart row itself is the truth.
const CART_KEY = 'till.cart'

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
  const [openCartCount, setOpenCartCount] = useState(0)
  const [panelOpen, setPanelOpen] = useState(false)
  const [rows, setRows] = useState<InventoryRow[]>([])
  const [chip, setChip] = useState<string>('ALL')
  // One field does both jobs, like the artifact: typing narrows the quick picks live, and Enter
  // tries the text as a scanned/keyed code. A scanner is just a fast keyboard ending in Enter,
  // so the hardware path and the search path are literally the same input.
  const [entry, setEntry] = useState('')
  // The pay flow: idle → the customer ask (policy: ask every sale; Walk-in declines in one tap)
  // → the payment method. The customer rides the CART (a hold keeps the person), never client state.
  const [payStep, setPayStep] = useState<'idle' | 'customer' | 'method'>('idle')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [manualOpen, setManualOpen] = useState(false)

  const adoptCart = (c: CartView) => {
    localStorage.setItem(CART_KEY, c.cartId)
    setCart(c)
  }

  // Restore the remembered cart, or start fresh — a reload changes nothing (spec: checkout).
  const loadCart = async () => {
    try {
      const remembered = localStorage.getItem(CART_KEY)
      const restored = remembered ? await checkout.restore(remembered) : null
      adoptCart(restored ?? await checkout.openFor(register.registerName))
    } catch {
      setError('Cannot reach the till.')
    }
  }

  const refreshCount = () => {
    checkout.openCarts().then((cs) => setOpenCartCount(cs.length)).catch(() => {})
  }

  useEffect(() => {
    loadCart()
    refreshCount()
    inventory.rows().then(setRows).catch(() => {})
    // Another till may have taken or changed this cart — truth on every return to the window.
    const onFocus = () => {
      const remembered = localStorage.getItem(CART_KEY)
      if (remembered) {
        checkout.restore(remembered)
          .then((c) => { if (c) setCart(c); else loadCart() })
          .catch(() => {})
      }
      refreshCount()
    }
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Hold: park this cart (it stays open, customer attached) and start a fresh one.
  const holdCart = async () => {
    try {
      adoptCart(await checkout.openFor(register.registerName))
      setPayStep('idle')
      refreshCount()
    } catch {
      setError('Cannot reach the till.')
    }
  }

  // Resume a cart from the panel onto this screen — any till resumes any cart.
  const resumeCart = async (cartId: string) => {
    try {
      const c = await checkout.restore(cartId)
      if (c) adoptCart(c)
      setPanelOpen(false)
      setPayStep('idle')
      refreshCount()
    } catch {
      setError('Cannot reach the till.')
    }
  }

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
      localStorage.removeItem(CART_KEY)
      setPayStep('idle')
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      setBusy(false)
    }
  }

  const newSale = () => {
    setSale(null)
    checkout.openFor(register.registerName).then(adoptCart).catch(() => setError('Cannot reach the till.'))
    refreshCount()
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
            // Disabled until the cart is open: a hardware scanner can finish its burst before the
            // open() round-trip lands, and a keystroke into a null cart would die silently.
            disabled={!cart}
            onChange={(e) => setEntry(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onScan(entry)}
          />
          <button type="button" className="pos-manual" onClick={() => setManualOpen(true)}>
            Manual entry
          </button>
        </div>
        {manualOpen && cart && (
          <ManualEntryDialog
            cartId={cart.cartId}
            onClose={() => setManualOpen(false)}
            onAdded={(next) => { setCart(next); setManualOpen(false) }}
          />
        )}
        <div className="mode-toggle pos-chips">
          {chips.map((c) => (
            <button key={c} type="button" className={chip === c ? 'on' : ''} onClick={() => setChip(c)}>
              {c === 'ALL' ? 'Quick picks' : c}
            </button>
          ))}
        </div>
        <div className="pos-grid">
          {picks.map((r) => (
            <button key={r.productId} type="button" className="pos-tile" title={r.productName}
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
          <button type="button" className="pos-carts-pill" onClick={() => { setPanelOpen(true); refreshCount() }}>
            Carts <span className="pos-carts-count">{openCartCount}</span>
          </button>
          <button type="button" className="pos-clear"
            onClick={() => cart && mutate(async () => {
              await checkout.attachCustomer(cart.cartId, null)
              return checkout.clear(cart.cartId)
            })}>
            Clear
          </button>
        </div>
        <div className="text-muted" style={{ marginBottom: 8 }}>
          {cart?.customerName ?? 'Walk-in customer'} · {register.registerName} · {register.operatorName}
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

        {payStep === 'idle' && (
          <>
            <button className="pos-pay" disabled={items === 0 || busy}
              onClick={() => setPayStep(cart?.customerId ? 'method' : 'customer')}>
              Take payment
            </button>
            <div className="pos-rowbtns">
              <button type="button" className="pos-hold" title="Park this cart and start a fresh one"
                disabled={items === 0} onClick={holdCart}>
                Hold cart
              </button>
            </div>
          </>
        )}
        {payStep === 'customer' && cart && (
          <CustomerStep
            onAttach={async (c) => {
              await mutate(() => checkout.attachCustomer(cart.cartId, c.id))
              setPayStep('method')
            }}
            onWalkIn={() => setPayStep('method')}
          />
        )}
        {payStep === 'method' && (
          <div className="pos-methods">
            {(['CASH', 'UPI', 'CARD'] as PaymentMethod[]).map((m) => (
              <button key={m} disabled={busy} onClick={() => takePayment(m)}>{m}</button>
            ))}
            <button disabled={busy} onClick={() => setPayStep('idle')}>Back</button>
          </div>
        )}
      </aside>
      {panelOpen && cart && (
        <CartsPanel currentCartId={cart.cartId} onResume={resumeCart} onClose={() => setPanelOpen(false)} />
      )}
    </div>
  )
}

/**
 * Manual entry: sell a thing with no product record — name and price keyed at the counter.
 * GST comes from a picked sub-category (or the shop default); the lot is optional attribution
 * to the delivery it came from, for recovery reporting. No stock is decremented — the stock was
 * never in the system, which is why it is being keyed by hand.
 */
function ManualEntryDialog({ cartId, onClose, onAdded }: {
  cartId: string
  onClose: () => void
  onAdded: (cart: CartView) => void
}) {
  const [name, setName] = useState('')
  const [priceRupees, setPriceRupees] = useState('')
  const [mrpRupees, setMrpRupees] = useState('')
  const [subCategory, setSubCategory] = useState('')
  const [lotId, setLotId] = useState('')
  const [gst, setGst] = useState<{ defaultBasisPoints: number; options: { subCategory: string; basisPoints: number }[] } | null>(null)
  const [lots, setLots] = useState<LotSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    checkout.gstOptions().then(setGst).catch(() => {})
    receiving.lots().then(setLots).catch(() => {})
  }, [])

  const paise = (s: string) => Math.round(parseFloat(s || '0') * 100)

  const add = async () => {
    setBusy(true)
    setError(null)
    try {
      onAdded(await checkout.addCustomLine(
        cartId, name.trim(), paise(priceRupees),
        mrpRupees.trim() ? paise(mrpRupees) : null,
        subCategory || null, lotId || null))
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="pos-dialog-scrim" onClick={onClose}>
      <div className="pos-dialog" onClick={(e) => e.stopPropagation()}>
        <h2 style={{ marginTop: 0 }}>Manual entry</h2>
        {error && <div className="banner stop">{error}</div>}
        <label>
          What is being sold
          <input value={name} autoFocus onChange={(e) => setName(e.target.value)} placeholder="e.g. Loose glass jar" />
        </label>
        <div className="pos-dialog-row">
          <label>
            Price (₹)
            <input value={priceRupees} inputMode="decimal" onChange={(e) => setPriceRupees(e.target.value)} />
          </label>
          <label>
            MRP (₹, optional)
            <input value={mrpRupees} inputMode="decimal" onChange={(e) => setMrpRupees(e.target.value)} />
          </label>
        </div>
        <label>
          GST category
          <select value={subCategory} onChange={(e) => setSubCategory(e.target.value)}>
            <option value="">Default ({gst ? gst.defaultBasisPoints / 100 : 18}%)</option>
            {gst?.options.map((o) => (
              <option key={o.subCategory} value={o.subCategory}>
                {o.subCategory} · {o.basisPoints / 100}%
              </option>
            ))}
          </select>
        </label>
        <label>
          From delivery (optional)
          <select value={lotId} onChange={(e) => setLotId(e.target.value)}>
            <option value="">Not attributed</option>
            {lots.map((l) => (
              <option key={l.id} value={l.id}>
                {l.supplier} · {l.receivedOn}{l.categoryCode ? ` · ${l.categoryCode}` : ''}
              </option>
            ))}
          </select>
        </label>
        <div className="pos-dialog-actions">
          <button disabled={busy || !name.trim() || !paise(priceRupees)} className="pos-pay" style={{ marginTop: 0 }} onClick={add}>
            Add to cart
          </button>
          <button disabled={busy} onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  )
}

/**
 * The counter's ask, every sale: key the mobile, a match confirms by name, a new pair saves —
 * and Walk-in is always one tap (spec: selling-screens). A lookup that cannot be reached also
 * proceeds as walk-in: the customer step must never block a sale.
 */
function CustomerStep({ onAttach, onWalkIn }: {
  onAttach: (c: CustomerView) => void
  onWalkIn: () => void
}) {
  const [mobile, setMobile] = useState('')
  const [name, setName] = useState('')
  const [found, setFound] = useState<CustomerView | null>(null)
  const [lookedUp, setLookedUp] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const digits = mobile.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '').replace(/^0(?=\d{10}$)/, '')

  useEffect(() => {
    setFound(null)
    setLookedUp(false)
    setError(null)
    if (digits.length !== 10) return
    let stale = false
    customersApi.byMobile(digits)
      .then((c) => { if (!stale) { setFound(c); setLookedUp(true) } })
      .catch(() => { if (!stale) { setError('Lookup unreachable — Walk-in still works.'); setLookedUp(true) } })
    return () => { stale = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [digits])

  const saveAndAttach = async () => {
    setBusy(true)
    setError(null)
    try {
      const c = await customersApi.save(name.trim(), digits)
      if (c) onAttach(c)
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot save — Walk-in still works.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="pos-customer">
      <div className="text-muted" style={{ marginBottom: 6 }}>Customer's mobile — or Walk-in</div>
      {error && <div className="banner stop" style={{ margin: '0 0 8px' }}>{error}</div>}
      <input
        autoFocus
        inputMode="tel"
        placeholder="Mobile number"
        value={mobile}
        onChange={(e) => setMobile(e.target.value)}
      />
      {found && (
        <button className="pos-pay" style={{ marginTop: 8 }} disabled={busy} onClick={() => onAttach(found)}>
          {found.name} — attach
        </button>
      )}
      {!found && lookedUp && digits.length === 10 && (
        <>
          <input
            placeholder="Customer's name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={{ marginTop: 8 }}
          />
          <button className="pos-pay" style={{ marginTop: 8 }} disabled={busy || !name.trim()} onClick={saveAndAttach}>
            Save and attach
          </button>
        </>
      )}
      <button className="pos-walkin" disabled={busy} onClick={onWalkIn}>Walk-in</button>
    </div>
  )
}

/**
 * The open carts across the shop, newest touch first — who each is for, what's in it, where it
 * sits. Tapping a row previews its lines inline; Resume loads it onto this screen (any till
 * resumes any cart). The cart already on this screen is marked and not resumable into itself.
 */
function CartsPanel({ currentCartId, onResume, onClose }: {
  currentCartId: string
  onResume: (cartId: string) => void
  onClose: () => void
}) {
  const [carts, setCarts] = useState<CartSummary[]>([])
  const [preview, setPreview] = useState<CartView | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    checkout.openCarts().then(setCarts).catch(() => setError('Cannot reach the till.'))
  }, [])

  const togglePreview = async (cartId: string) => {
    if (preview?.cartId === cartId) {
      setPreview(null)
      return
    }
    try {
      setPreview(await checkout.restore(cartId))
    } catch {
      setError('Cannot reach the till.')
    }
  }

  const ago = (iso: string) => {
    const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
    return mins === 0 ? 'just now' : mins === 1 ? '1 min ago' : mins < 60 ? `${mins} min ago`
      : `${Math.floor(mins / 60)} h ago`
  }

  return (
    <div className="pos-panel-scrim" onClick={onClose}>
      <aside className="pos-panel" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>Open carts</h3>
          <button type="button" className="pos-clear" onClick={onClose}>Close</button>
        </header>
        {error && <div className="banner stop" style={{ margin: '8px 12px' }}>{error}</div>}
        <div className="pos-panel-rows">
          {carts.map((c) => (
            <div key={c.cartId}>
              <button type="button"
                className={c.cartId === currentCartId ? 'pos-cartrow on' : 'pos-cartrow'}
                onClick={() => c.cartId !== currentCartId && togglePreview(c.cartId)}>
                <span className="pos-cartrow-who">
                  {c.customerName ?? 'Walk-in'}
                  {c.cartId === currentCartId
                    ? <span className="pos-chip this">This screen</span>
                    : c.registerName && <span className="pos-chip">{c.registerName}</span>}
                </span>
                <span className="pos-cartrow-when">{ago(c.touchedAt)}</span>
                <span className="pos-cartrow-sum">{c.itemCount} item{c.itemCount === 1 ? '' : 's'} · {c.summary}</span>
                <span className="pos-cartrow-amt">{rupees(c.totalPaise)}</span>
              </button>
              {preview !== null && preview.cartId === c.cartId && c.cartId !== currentCartId && (
                <div className="pos-panel-preview">
                  {preview.lines.map((l) => (
                    <div key={l.lineId} className="pos-line" style={{ padding: '5px 0' }}>
                      <span>{l.name}{l.quantity > 1 ? ` × ${l.quantity}` : ''}</span>
                      <b>{rupees(l.lineTotalPaise)}</b>
                    </div>
                  ))}
                  {preview.lines.length === 0 && <div className="text-muted">Empty cart.</div>}
                  <button type="button" className="pos-pay" style={{ marginTop: 8 }}
                    onClick={() => onResume(c.cartId)}>
                    Resume this cart here
                  </button>
                </div>
              )}
            </div>
          ))}
          {carts.length === 0 && <div className="text-muted" style={{ padding: 16 }}>No open carts.</div>}
        </div>
        <div className="pos-panel-eod">Carts left from a previous day abandon themselves at first touch each morning.</div>
      </aside>
    </div>
  )
}
