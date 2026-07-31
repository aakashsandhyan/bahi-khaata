import { useEffect, useRef, useState } from 'react'
import { checkout, BackendError } from './api'
import type { CartLineView, CartView } from './types'
import { rupees } from './money'

/**
 * The till. Scan → cart → pay → receipt, in as few screens as the work allows.
 *
 * Speed first: the scan field is always at the top and always focused, the cart is on the same
 * screen as the total so nothing is a click away, and the saving against MRP rides on every line
 * and on the whole — because being cheaper than online is what the shop is, not a footnote.
 *
 * Errors are shown inline, never as a popup that blocks the counter. A scan that will not sell
 * says why, and the person carries on.
 */
export function Checkout() {
  const [cart, setCart] = useState<CartView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const scanRef = useRef<HTMLInputElement>(null)
  const focusScan = () => setTimeout(() => scanRef.current?.focus(), 0)

  useEffect(() => {
    checkout.open().then(setCart).catch(() => setError('Cannot reach the till.'))
  }, [])

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

  if (!cart) return <div className="till">{error ?? 'Opening the till…'}</div>

  const empty = cart.lines.length === 0

  return (
    <div className="till">
      <input
        ref={scanRef}
        className="till-scan"
        autoFocus
        placeholder="Scan barcode or product code"
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
            <div className="row muted">
              <span>
                Tax (GST){cart.taxIsPlaceholder && <em className="tbd"> · estimate</em>}
              </span>
              <span>{rupees(cart.taxPaise)}</span>
            </div>
            <div className="row total">
              <span>Total</span>
              <span>{rupees(cart.totalPaise)}</span>
            </div>
          </div>

          {cart.savingPaise > 0 && (
            <div className="till-saved">💰 You saved {rupees(cart.savingPaise)}</div>
          )}

          <div className="till-actions">
            <button onClick={() => change(() => checkout.clear(cart.cartId))}>Clear</button>
            <button className="till-pay" onClick={() => setError('Payment is coming next — cart works, till does not take money yet.')}>
              Pay now
            </button>
          </div>
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
