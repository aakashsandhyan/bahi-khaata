## Context

Phase 1 (palletworks-foundation) gave the app one visual language, a grouped sidebar with state-based view switching (`App.tsx` holds `view`; `Sidebar` takes an `onNavigate` prop), and a Playwright smoke suite (16 specs) that boots the real Spring Boot jar against a freshly-migrated scratch SQLite loaded by a repeatable seed. The bill-printing merge added `sale`/`sale_line` (V42) as the real revenue record. This change builds the Dashboard screen: one read-only aggregate over data other components already own — `sale`, `stock_ledger`, `batch`, `product`, `lot`, `product_capture`, `print_job` — surfaced as KPI tiles, a received→priced→sold funnel, real-signal alerts, and the five most recent sales. No schema change, no writes, no behaviour change to any other screen.

Repositories are Spring Data JPA. Shared response records live in the `contracts` module (`com.bahikhaata.contracts`, e.g. `SaleSummary`), with the frontend mirroring them by hand in `types.ts`. Timestamps are ISO-8601 UTC text with fixed-width millis (`2026-08-01T09:46:00.000Z`), chronologically sortable as strings. The shop runs in IST.

## Goals / Non-Goals

**Goals:**
- One aggregate endpoint that answers "how did we sell today, what is stuck, what needs a decision" in a single round trip, fast on shop hardware.
- A `Dashboard.tsx` that renders it and whose alert rows navigate to the owning screen via the existing switch.
- Deterministic e2e: seeded sales let the revenue tile assert exact figures; the existing 16 specs keep passing.

**Non-Goals:**
- No GST computation (the tile reads `sale.tax_paise`, 0 until the gst-inclusive-pricing change ships).
- No charts, no category recovery, no sell-through curves (Analytics phase).
- No router; no till or write-path change; no new schema.

## Decisions

**D1 — One endpoint `GET /api/dashboard`, read-only, one DTO for the whole screen.**
The screen is a single glance; one call keeps it consistent and cheap. *Rejected:* per-widget endpoints (four+ round trips, tiles can disagree mid-refresh); client-side composition (moves aggregation logic and N+1 into the browser).

**D2 — The DTO (`DashboardView`, nested records per section) lives in the `contracts` module.**
Matches every other response type; the frontend mirror in `types.ts` stays a deliberate hand-copy. *Rejected:* defining it inside the backend `dashboard` package (breaks the contracts convention and the shared-record boundary).

**D3 — The `dashboard` package owns a read-only `DashboardRepository` (native single-pass aggregate queries); it reuses a component's public API only where one already answers the question — `Checkout.recentSales(5)` for the recent-sales rail.**
Keeps dashboard read-logic in one reviewable place and respects the component boundary (it calls public APIs, never other packages' repositories/internals). *Rejected:* adding aggregate methods to each component's repository (spreads dashboard concerns across five packages); loading rows and summing in Java (N+1 over an append-only ledger that only grows).

**D4 — "Today" is an IST calendar-day window computed in Java (`ZoneId.of("Asia/Kolkata")`), converted to two UTC ISO-8601 instant strings, and applied as a half-open range `created_at >= :startUtc AND created_at < :endUtc`.**
`created_at` sorts correctly as text, so a string range is exact and index-friendly (`idx_sale_created_at`). *Rejected:* SQLite `date(created_at)` on UTC text — IST is UTC+5:30, so an early-IST-morning sale carries the previous UTC date and would be dropped from (or misattributed across) "today".

**D5 — Four KPI tiles, each honest about its source.**
Revenue today = `SUM(sale.total_paise)` in the window, bill count, and average guarded against zero bills; received-vs-priced units with the unpriced backlog; all-time recovery = all-time `SUM(sale.total_paise) ÷ SUM(lot.amount_paid_paise)`; GST collected = `SUM(sale.tax_paise)`, labelled "not yet computed" so ₹0 never reads as a real revenue-neutral zero. *Rejected:* hiding the GST tile until gst-inclusive-pricing ships (the strip's shape would shift under operators later — wire it now, label it honestly); reporting a 0 average when there are no bills.

**D6 — The funnel is one pass over the ledger joined to product price.**
Received units = `SUM(quantity)` where `movement_type='PURCHASE_RECEIPT'`; priced/on-floor = on-hand (`SUM(quantity)`) for products with `selling_price_paise` set; sold = `SUM(-quantity)` where `movement_type='SALE'`; MRP value per stage from `batch.mrp_paise`. *Rejected:* three separate table scans; deriving "priced" from `label_printed_at` (a printed label is not a set price).

**D7 — Alerts are real counts only, each carrying the `View` it opens; a zero-count signal is omitted, never shown as "0".**
Signals: unpriced counted units (`selling_price_paise IS NULL` with ledger receipts) → Pricing; NEEDS_WORK backlog (off-ledger `batch` qty) → Prep; pending `product_capture` → Review; `print_job` status `'review'` → Reprint; open lots (`receiving_complete=0`) with `received_on` older than N days → Lots. *Rejected:* heuristic/synthetic alerts; always-present rows that cry "0 to do".

**D8 — `Dashboard` receives `onNavigate: (v: View) => void` from `App` (the same prop `Sidebar` already takes); each alert row calls it with its target view.**
One navigation mechanism app-wide, no new plumbing. *Rejected:* hash/router links (phase 1 chose state switching on purpose); a bespoke event bus.

**D9 — Add view `'dashboard'` as the first item of the Operations group (kicker "Overview"); desktop landing state changes `checkout`→`dashboard`; phone landing (Unpacking / Capture) untouched.**
Mirrors the approved mockup, which lists Dashboard first under Operations. *Rejected:* a one-item top-level "Home" group (over-structured); parking it under Selling (it summarizes operations too, not just sales).

**D10 — e2e seed adds two `sale` rows (+ their `sale_line` rows) stamped with `strftime('%Y-%m-%dT%H:%M:%S.000Z','now')` so they land in today's IST window at apply time; amounts are fixed constants the spec asserts exactly.**
The seed is a Flyway repeatable migration whose SQL text stays constant (so its checksum is stable); `now()` evaluates at apply time, and the harness recreates the DB every run, so the two sales are always "today". *Rejected:* fixed literal timestamps (they fall out of "today" the next day); raw `datetime('now')` (space-separated, no `Z`, no millis — its format would not match the app's ISO-8601 strings and the string-range filter would silently miss it, leaving the tile at 0 and the assertion the only tripwire).

## Risks / Trade-offs

- [Aggregate scans over the growing append-only ledger slow the payload] → single-pass indexed queries (`idx_ledger_product_effective`, `idx_sale_created_at`), no N+1; timing observed in the smoke run.
- [GST ₹0 misread as a real figure] → tile carries a "not yet computed — wired for gst-inclusive-pricing" label, not a bare ₹0.
- [Recovery ratio excludes `lot.freight_paise`, slightly overstating recovery vs true landed cost] → uses the "amount paid" figure operators recognise; a landed-cost variant is deferred to Analytics.
- [Dashboard becomes the desktop landing, so a failing aggregate could greet every open] → endpoint is read-only and fast; `Dashboard.tsx` shows a plain error state like other screens and never wedges navigation.
- [Seed timestamp-format drift filters to 0 silently] → seed formats ISO-8601-Z via `strftime`; the dashboard spec asserts a non-zero exact revenue, so any format regression fails the suite loudly.

## Migration Plan

1. No schema change. New backend `dashboard` controller/service/`DashboardRepository` + `DashboardView` in contracts; `Dashboard.tsx` + nav/landing edits (`Sidebar.tsx` view type + entry, `App.tsx` landing + render chain, `api.ts`/`types.ts`); e2e seed rows + `13-dashboard.spec.ts`.
2. `npm run e2e` green (16 existing + new dashboard spec) is the merge gate.
3. Deploy = existing jar-swap (the dashboard is served by the backend jar); no DB or config migration. Rollback = previous jar.

## Open Questions

- Alert staleness threshold **N days** — ships as a named constant with a default of 3 and an explaining comment; revisit once the shop says what "stale intake" means to them. Non-blocking.
