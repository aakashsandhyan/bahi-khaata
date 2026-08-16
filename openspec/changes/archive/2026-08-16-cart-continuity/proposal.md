# Cart Continuity + Invoice Details

## Why

A refresh births a new cart today — the screen calls open() on every mount, orphaning whatever was in progress — when the intent was always the opposite: a cart persists until paid, cleared, or the day ends. The counter also needs more than one cart alive at once (Meera forgets her wallet; the queue cannot wait), and Invoices answers "what was on that bill?" only by reprinting paper. The UX for all three is settled in the approved comp (`reference/cart-continuity-ux.html`).

## What Changes

- **Refresh-proof cart**: the device remembers its cart and re-opens it after any reload. A cart dies only by payment, by Clear, or by the morning sweep.
- **Hold + multi-cart**: Hold parks the current cart (it stays open, customer attached) and opens a fresh one. A "Carts (n)" control on the cart pane lists all open carts newest-touched first, with who/summary/amount/register, an inline preview of a tapped row's lines, and **Resume this cart here** — any till resumes any cart.
- **EOD sweep**: open carts from a previous business day (IST) auto-abandon lazily — at the first read that sees them next morning. No scheduler.
- **Customer moves to the cart** (V53, additive): the capture step attaches the customer to the cart at attach time, so a held cart keeps its person; completing copies the cart's customer onto the sale. The pay-step ask and one-tap Walk-in stay exactly as shipped.
- **Invoice details modal**: any Invoices row opens the full stored bill — lines with qty × price and per-line saving, the GST split as invoiced, operator/register/customer, Reprint in the modal. The Invoices table gains a Customer column.

## Capabilities

### New Capabilities

(none — everything here extends existing capabilities)

### Modified Capabilities
- `checkout`: cart lifecycle gains persistence rules — survives reload, holdable, resumable from any register, listable newest-first, swept at day change; cart carries the optional customer that completing copies to the sale.
- `selling-screens`: the POS gains the carts control/panel/preview and Hold; Invoices gains the details modal and customer column.

## Impact

- Backend: `GET /api/checkout/carts` (open carts, updated_at desc, with summary), V53 `cart.customer_id` + attach endpoint, lazy sweep applied in the list/open paths. Sale completion copies the cart's customer (request-level customerId retires in the modern flow but stays accepted).
- Frontend: `CheckoutModern` — cartId in localStorage, re-open on mount, Hold button, carts panel with preview/resume per the comp; Invoices modal per the comp.
- Builds on `feature/customer-capture` (same branch stack); no changes to sale recording, GST, or receipts.
