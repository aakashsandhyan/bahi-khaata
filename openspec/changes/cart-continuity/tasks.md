# Tasks — Cart Continuity + Invoice Details

## 1. Cart lifecycle (backend)

- [ ] 1.1 Migration V53 (verify ceiling first): nullable `cart.customer_id` + `cart.register_name` — additive only
- [ ] 1.2 Touch discipline: every cart mutation (scan, add-product, custom line, quantity, remove, clear, customer attach) explicitly touches the cart row so last-touch ordering is honest
- [ ] 1.3 Sweep guard: shared check marking open carts last touched before today (IST) abandoned; applied in the carts-list read and the fetch-by-id read
- [ ] 1.4 `GET /api/checkout/carts`: open carts touched-desc with the panel's shape (customer name, item count, first-line + "+N more", total, register, last touch); `open()` accepts an optional register name to stamp
- [ ] 1.5 `POST /api/checkout/cart/{id}/customer`: attach/detach the cart's customer; `CartView` gains the customer name; complete copies cart→sale (request-level customerId honored as fallback)
- [ ] 1.6 Tests: reload-refetch returns same cart; touch ordering; sweep on both read paths (yesterday abandons, today survives); double-complete refused after cross-register resume; held cart's customer rides to the sale; fallback customerId still works

## 2. POS continuity (frontend)

- [ ] 2.1 Cart pointer in localStorage: mount restores the remembered cart (fresh on missing/paid/abandoned/swept); refetch on window focus
- [ ] 2.2 Hold control beside Clear: parks the cart, opens fresh; capture step writes the customer to the cart at attach (replacing client-only state); header keeps naming the cart's customer
- [ ] 2.3 Carts pill + panel per the comp: count, rows newest-first with who/summary/amount/register/when, this-screen marked, inline preview on tap, Resume loads the cart here
- [ ] 2.4 e2e: reload keeps the cart; hold → ring another → resume returns lines + customer; panel order and this-screen marker

## 3. Invoice details modal

- [ ] 3.1 `SaleSummary.customerName` + `SaleView` customer fields (name, masked mobile); Invoices table customer column (Walk-in when none)
- [ ] 3.2 Modal per the comp: lines with qty × price and saving, GST split, method/operator/register/customer facts, Reprint, scrim/Close dismiss
- [ ] 3.3 e2e: row opens modal with seeded figures; walk-in labeled; reprint reachable

## 4. Verify

- [ ] 4.1 Full backend suite green
- [ ] 4.2 e2e suite green
- [ ] 4.3 Browser walk on the running app: build cart → reload → hold → second cart → resume across registers → pay; open the bill from Invoices
- [ ] 4.4 `openspec validate cart-continuity`
