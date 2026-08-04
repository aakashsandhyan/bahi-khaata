import { useEffect, useRef, useState } from 'react'
import { checkout, sales, BackendError } from './api'
import type { CartLineView, CartView, PaymentMethod, SaleView } from './types'
import { rupees } from './money'

const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'UPI', 'CARD']

/**
 * The till. Scan → cart → pay → receipt, in as few screens as the work allows.
 *
 * Speed first: the scan field is always at the top and always focused, the cart is on the same
 * screen as the total so nothing is a click away, and the saving against MRP rides on every line
 * and on the whole — because being cheaper than online is what the shop is, not a footnote.
 *
 * Completing takes one payment method and rings the sale. The bill prints on the counter; if it did
 * not (printer offline), the confirmation says so and offers a reprint — the sale is recorded either
 * way. Errors are shown inline, never as a popup that blocks the counter.
 */
export function Checkout() {
  const [cart, setCart] = useState<CartView | null>(null)
  const [error, setError] = useState<string | null>(null)
  // The counter flows cart → paying (pick a method) → done (the recorded bill).
  const [paying, setPaying] = useState(false)
  const [sale, setSale] = useState<SaleView | null>(null)
  const [busy, setBusy] = useState(false)
  // Who is at the till — remembered per device, like pricing, so it is typed once a shift.
  const [operator, setOperator] = useState(() => localStorage.getItem('till.operator') ?? '')

  const scanRef = useRef<HTMLInputElement>(null)
  const focusScan = () => setTimeout(() => scanRef.current?.focus(), 0)

  useEffect(() => {
    checkout.open().then(setCart).catch(() => setError('Cannot reach the till.'))
  }, [])

  const setOperatorName = (name: string) => {
    setOperator(name)
    localStorage.setItem('till.operator', name)
  }

  const onScan = async (raw: string) => {
    const code = raw.trim()
    if (!code || !cart) return
    setError(null)
    try {
      setCart(await checkout.scan(cart.cartId, code))
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      focusScan()
    }
  }

  const change = async (fn: () => Promise<CartView>) => {
    try {
      setCart(await fn())
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      focusScan()
    }
  }

  const completeWith = async (method: PaymentMethod) => {
    if (!cart || busy) return
    setBusy(true)
    setError(null)
    try {
      setSale(await checkout.complete(cart.cartId, method, operator.trim() || null))
      setPaying(false)
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the till.')
    } finally {
      setBusy(false)
    }
  }

  const reprint = async () => {
    if (!sale || busy) return
    setBusy(true)
    try {
      setSale(await sales.reprint(sale.saleId))
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the printer.')
    } finally {
      setBusy(false)
    }
  }

  const newSale = async () => {
    setSale(null)
    setError(null)
    try {
      setCart(await checkout.open())
    } catch {
      setError('Cannot reach the till.')
    } finally {
      focusScan()
    }
  }

  // The recorded bill — the sale is done, stock has moved. Print status is honest here.
  if (sale) {
    return (
      <div className="till till-done">
        <div className="till-done-num">{sale.billNoFormatted}</div>
        <div className="till-done-total">{rupees(sale.totalPaise)}</div>
        <div className="till-done-meta">
          Paid {sale.paymentMethod}
          {sale.savingPaise > 0 && <> · saved {rupees(sale.savingPaise)}</>}
        </div>
        {sale.printFailed ? (
          <div className="till-error">The bill did not print. The sale is recorded — reprint it.</div>
        ) : (
          <div className="till-saved">🧾 Bill printed.</div>
        )}
        <div className="till-actions">
          <button onClick={reprint} disabled={busy}>
            {busy ? 'Printing…' : 'Reprint bill'}
          </button>
          <button className="till-pay" onClick={newSale} disabled={busy}>
            New sale
          </button>
        </div>
      </div>
    )
  }

  if (!cart) return <div className="till">{error ?? 'Opening the till…'}</div>

  const empty = cart.lines.length === 0

  return (
    <div className="till">
      <input
        ref={scanRef}
        className="till-scan"
        autoFocus
        placeholder="Scan barcode or product code"
        disabled={paying}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            onScan((e.target as HTMLInputElement).value)
            ;(e.target as HTMLInputElement).value = ''
          }
        }}
      />

      {error && <div className="till-error">{error}</div>}

      <div className="till-cart">
        {empty && <div className="till-empty">Scan the first item to begin.</div>}
        {cart.lines.map((line) => (
          <Line
            key={line.lineId}
            line={line}
            onLess={() =>
              change(() =>
                line.quantity <= 1
                  ? checkout.removeLine(cart.cartId, line.lineId)
                  : checkout.setQuantity(cart.cartId, line.lineId, line.quantity - 1),
              )
            }
            onMore={() => change(() => checkout.setQuantity(cart.cartId, line.lineId, line.quantity + 1))}
          />
        ))}
      </div>

      {!empty && (
        <>
          <div className="till-totals">
            <div className="row">
              <span>Subtotal</span>
              <span>{rupees(cart.subtotalPaise)}</span>
            </div>
            <div className="row total">
              <span>Total</span>
              <span>{rupees(cart.totalPaise)}</span>
            </div>
          </div>

          {cart.savingPaise > 0 && (
            <div className="till-saved">You saved {rupees(cart.savingPaise)}</div>
          )}

          {paying ? (
            <div className="till-pay-panel">
              <input
                className="till-operator"
                placeholder="Who is at the till? (optional)"
                value={operator}
                onChange={(e) => setOperatorName(e.target.value)}
              />
              <div className="till-methods">
                {PAYMENT_METHODS.map((m) => (
                  <button key={m} className="till-method" disabled={busy} onClick={() => completeWith(m)}>
                    {m}
                  </button>
                ))}
              </div>
              <button className="till-cancel" disabled={busy} onClick={() => setPaying(false)}>
                Back
              </button>
            </div>
          ) : (
            <div className="till-actions">
              <button onClick={() => change(() => checkout.clear(cart.cartId))}>Clear</button>
              <button className="till-pay" onClick={() => { setError(null); setPaying(true) }}>
                Complete sale
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function Line({
  line,
  onLess,
  onMore,
}: {
  line: CartLineView
  onLess: () => void
  onMore: () => void
}) {
  return (
    <div className="till-line">
      <div className="till-line-main">
        <div className="till-line-name">{line.name.length > 48 ? line.name.slice(0, 45) + '…' : line.name}</div>
        {line.asin && <div className="till-line-asin">{line.asin}</div>}
        <div className="till-line-price">
          {/* Strike the MRP only when it is a real saving; a product with no MRP just shows its price. */}
          {line.savingPaise > 0 && <span className="mrp">{rupees(line.mrpPaise)}</span>}
          <span className="now">{rupees(line.unitPricePaise)}</span>
          {line.savingPercent > 0 && <span className="off">{line.savingPercent}% off</span>}
        </div>
      </div>
      <div className="qty">
        <button onClick={onLess} aria-label="one less">−</button>
        <span>{line.quantity}</span>
        <button onClick={onMore} aria-label="one more">+</button>
      </div>
      <div className="till-line-total">{rupees(line.lineTotalPaise)}</div>
    </div>
  )
}
