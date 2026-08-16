# Design — Cart Continuity + Invoice Details

## Context

Carts are already durable rows (open/paid/abandoned states exist); the frontend just never re-finds them — `open()` on every mount orphans the last cart. Completion is already once-only (`openCart` rejects a paid cart transactionally). Customer capture attaches client-side today, so any hold/resume would lose the person. The UX is settled: `reference/cart-continuity-ux.html`.

## Goals / Non-Goals

**Goals:**
- A device's cart survives reload; Hold parks; the carts panel lists/previews/resumes across registers; yesterday's carts sweep at first touch; a held cart keeps its customer; Invoices opens a full-bill modal.

**Non-Goals:**
- No cart locking or reservation UI; no scheduler; no classic-till changes; no receipt/GST changes; no cart history screen (abandoned carts stay invisible).

## Decisions

1. **No locks on resume — completion-once is the money guard.** Two tills touching one cart interleave lines visibly and harmlessly; a double *payment* is impossible (the second complete rejects on the paid cart). Resume simply loads the cart here; the other screen finds out on its next action. Two counters and shouting distance make coordination human, not mechanical. *Rejected:* claimed-by leases (state machine + expiry ceremony for a two-till shop).
2. **The device pointer is localStorage `till.cartId`.** Mount: `GET /cart/{id}` → open cart returns → use it; missing/paid/abandoned/swept → open fresh. Hold: drop the pointer, open fresh (the cart just stays open). Resume: set the pointer, fetch. The pointer is the only client state — everything else is the cart row.
3. **The sweep is a guard on the read paths, not a job.** A shared check — cart's last touch before today's IST date → mark abandoned — runs inside the carts-list query and the pointer-restore fetch. First person in each morning sweeps yesterday for everyone. *Rejected:* @Scheduled sweeper (another poller for something reads can do lazily and exactly).
4. **V53 (additive): `cart.customer_id` and `cart.register_name`.** The capture step writes the customer to the cart the moment it attaches (new `POST /cart/{id}/customer`), so a held cart keeps its person; complete copies cart→sale, with the request-level `customerId` still honored as fallback (classic till, API callers). The register name is stamped at open by the modern till (nullable — classic passes none) purely so the panel's register chip is honest.
5. **Last-touch is maintained, not computed.** Line mutations don't touch the cart row, so sorting by `updated_at` would lie; every cart mutation (scan, add, quantity, remove, clear, customer-attach) explicitly touches the cart. One line per mutation beats a max-over-lines join on every list read.
6. **The carts list is one endpoint with the panel's exact shape**: `GET /api/checkout/carts` → open carts, touched-desc, each with cartId, customer name (or null = Walk-in), item count, first-line name + "+N more", total paise, register name, last-touch. The panel renders it verbatim; the preview reuses the ordinary `GET /cart/{id}`.
7. **Invoice modal is frontend over existing reads**, plus names: `SaleSummary` gains `customerName` (the table's new column), `SaleView` gains `customerName`/`customerMobileMasked` for the modal's facts row. Reprint reuses the existing endpoint.

## Risks / Trade-offs

- [Stale second screen after a cross-register resume] → next mutation answers with the truth (changed cart or paid-cart rejection); cart refetch on window focus softens it further. Accepted for a two-till floor.
- [Sweep boundary races midnight] → the check compares IST dates on read; worst case a cart born 23:59 sweeps at 00:01 next touch — matches "abandon at first touch next morning" exactly.
- [Pointer to a cart another till emptied] → the cart view is refetched on mount/focus; the screen shows what the row truly holds.

## Migration Plan

V53 additive columns; no backfill (old carts are sessionless walk-ins, most already paid/swept). Ships on the `feature/customer-capture` stack — one PR, V52+V53 arrive together.

## Open Questions

- None blocking. The comp's "held on Register 2" chip shows the *opening* register, not who touched it last — acceptable first cut.
