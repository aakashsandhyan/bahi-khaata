## Context

Four Palletworks phases plus upstream merges have left sixteen desktop nav entries, several the shop never opens: three admin screens for one configuration concern, a Catalog screen whose browse/detail/count jobs Inventory and Item detail already largely serve, and a Till that has recorded zero web sales (the JavaFX terminal is the register; the web till is a later phase, decided 2026-08-05). Phase 4 folds these to twelve entries. The moving parts already exist and are honest: `App.tsx` holds `view` + `detailProductId` with a param-less `setView` and an `onOpenItem(id)` callback (no router — phase-1 decision); the catalog listing (`GET /api/catalog`, `ProductCatalog.browse`) already answers on-paper/found/department questions and is paged; `ProductCountPane` counts one product across a lot's boxes given a `lotId`; the three admin screens (`PrinterConfig`, `ReceiptPrinterConfig`, `BillSettings`) each fetch their config on mount. Catalog does **not** edit category today — it only sets price via `api.setPrice` — and no category-only write endpoint exists (`Product.setCategory` is reached only inside shelf pricing's save).

## Goals / Non-Goals

**Goals:**
- Twelve nav entries, each something the shop actually opens; every retired Catalog job re-homed or explicitly retired with a reason.
- Reuse the existing catalog listing for the on-paper gap; do not widen the inventory aggregate SQL.
- Keep Till in code and hash-reachable; delete the Catalog screen only after the rewritten suite is green.

**Non-Goals:**
- No router, no new screen props, no schema change, no printer/money-path change.
- No second price-write path; category edit is the only new write, and a minimal one.
- No live hash routing — a landing hook only, as `#capture` already is.

## Decisions

**D1 — Settings is a local-state tab shell hosting the three admin components unchanged, mounting one tab at a time.** `Settings.tsx` holds `tab` in local `useState`, renders only the active tab's component. Per-tab conditional mount (not keep-alive) preserves each admin screen's current per-navigation mount lifecycle — the dashboard-shell spec guarantees "same props and lifecycle" — so each tab shows freshly-fetched saved values, and no stale form state or three simultaneous fetches leak in. *Rejected:* keep-alive with CSS-hidden inactive tabs (holds three mounts and stale forms for a rarely-opened admin area, no benefit); a router or hash per tab (phase-1 chose state switching).

**D2 — On-paper and All scope reuse `GET /api/catalog` (`catalog.browse`); On floor keeps `GET /api/inventory`.** The catalog listing already answers "known-but-uncounted" with status/department/name filters and paging; the inventory aggregate is a per-product×condition financial scan that has no rows for an uncounted product. Inventory becomes the new (and only) caller of `catalog.browse`. *Rejected:* extending the inventory aggregate with a UNION over manifest-only products (widens a hot financial SQL to carry rows that have no financials, duplicating logic the catalog query already owns).

**D3 — The scope control swaps both dataset and column set, not a row filter over one table.** On floor renders the stock table (Condition, Lot, Bin, On hand, Cost, Price, Margin, Age + totals footer). On paper / All render the catalog columns (status badge, priced badge, counted/expected) — because an on-paper product has no stock, cost, or age, and seven blanked columns read as data loss, not honesty. Totals footer shows only in On floor. *Rejected:* one column set with em-dashes for paper rows (fake structure); fabricating zero cost/age for uncounted stock (lies).

**D4 — Category edit on Item detail is a new minimal endpoint `PATCH /api/products/{id}/category` → `Product.setCategory`.** No category-only write exists today: category is written only as a side-effect of `ShelfPricing.saveExisting` (which demands a `batchId`, a price, and is a reprice) or the bulk-print label edit (edits the queued entry, not the product). Reusing either would drag pricing semantics onto a plain reclassification. One tiny controller method on the existing `ProductController` is the honest home. *Rejected:* reusing `saveExisting` (requires a batch + repricing the product to change its department); a general product-PATCH (over-scoped — only category is being edited).

**D5 — Item detail's Count entry gets its lot context from a small delivery picker over the open deliveries, feeding `ProductCountPane` unchanged.** Catalog scoped counting through its screen-level delivery `<select>`; Item detail has none, so it loads the same open-deliveries list (`unpacking.deliveries`, filtered `!closed`) into an inline picker, preselecting when one open lot owes the product. `ProductCountPane` already returns an empty grid for a lot that owes nothing, so no product-scoped endpoint is needed. *Rejected:* a new "open lots owing this product" endpoint (the grid already degrades cleanly); counting with no lot chosen (a count belongs to a box in an open lot — the existing invariant).

**D6 — Item detail is the sole opener of the counting grid and is openable for on-paper products.** `onOpenItem(id)` is wired from On-paper/All rows too; `inventory.detail` resolves name/category/barcodes for an uncounted product with empty batch/movement/price sections, so Item detail is a valid landing for something you are about to count in. Catalog's detail-panel "across its boxes / still on paper" block retires — the expected-vs-counted gap stays a **list-row** fact (from `CatalogEntry`) in the On-paper scope, not duplicated onto the detail or bolted into `InventoryDetail`. *Rejected:* re-adding manifest expected/counted to `InventoryDetail` (widens the detail payload to restate a list fact).

**D7 — Generalize `App`'s initial-view computation to read the hash on every viewport, initial-only.** A `landingView()` maps `#till`→`checkout`, `#capture`→`capture` (phone), else desktop→`dashboard` / phone→`unpacking`. Today the hash is consulted only inside `phoneLanding`; `#till` on desktop must resolve to the Till screen the same param-less way `#capture` does. No `hashchange` listener. *Rejected:* a hashchange listener / router (Till revival is a later phase; only a landing hook is needed); special-casing `#till` inside the phone branch (leaves desktop unreachable).

**D8 — Till leaves the nav but stays in code and hash-reachable, unlisted like Capture.** The `checkout` view and `Checkout` component are untouched; only its `NAV_GROUPS` entry is removed. `screenMeta('checkout')` keeps its kicker/title for the header when reached by `#till`. *Rejected:* deleting the Till screen (the web-till-parity phase revives it — decided 2026-08-05).

**D9 — Deletion order: re-home, rewrite specs, go green, then delete the Catalog screen last.** (1) Settings shell + nav; (2) generalized hash landing + Till de-listed; (3) Inventory scope control wired to `catalog.browse`, Item detail gains category edit + count entry; (4) rewrite specs 01/08/11/15/16 and prove the new homes; (5) only once green, delete `Catalog.tsx`, its `catalog` nav entry, and the now-orphaned `catalog.detail` (used only by Catalog). The backend catalog API, `ProductCatalog`, and `catalog.browse` stay — Inventory's On-paper scope depends on them. *Rejected:* deleting Catalog first (strands the on-paper and counting entry points mid-fold — the proposal's named risk).

**D10 — At sync, `product-catalog` is trimmed, not deleted.** Its durable product-data requirements (label-printed marker, MRP-per-batch, online price) stay — they are product facts, not the screen. Its browse requirements (name-filtered listing, found-vs-on-paper, on-paper-surfaced-first, department filter) move to `inventory-view` as the scope control; opening-to-detail and set-price are already owned by `item-detail`; counting-is-a-hand-off is retained but re-triggered from Item detail. The "shared product-finder screen" framing is removed. *Rejected:* deleting the whole capability (would drop MRP/label/online-price requirements that have no other home).

## Risks / Trade-offs

- [Inventory becomes `catalog.browse`'s only caller — a catalog API change now silently breaks Inventory] → the contract (`CatalogEntry`) is unchanged and covered by the rewritten Inventory-scope smoke; the coupling is explicit in this design.
- [Per-tab remount in Settings re-fetches config on every tab click] → intended (D1); admin screens are low-traffic and a re-fetch always shows the freshest saved values.
- [Item detail opened for an on-paper product shows empty stock sections] → expected and honest; the sections already render their own empty states, and the Count action is the point of opening it.
- [`#till` landing is initial-only, so clicking Till-less nav never returns there] → acceptable; Till is a deliberately hidden back-door until its revival phase, not a daily destination.

## Migration Plan

1. Frontend, additive first: `Settings.tsx` (tab shell); `Sidebar.tsx` nav 16→12 (remove three admin + Catalog + Till entries, add Settings); `App.tsx` `landingView()` + Settings wiring; `Inventory.tsx` scope control (On floor / On paper / All) with per-scope dataset + columns; `ItemDetail.tsx` category edit + delivery-picker count entry; `api.ts` category PATCH.
2. Backend: one method `PATCH /api/products/{id}/category` on `ProductController` → `Product.setCategory`, validated like the existing edits; a unit test. No other backend change.
3. Rewrite e2e 01 (Till reached via `#till`, absent from nav), 08 (Catalog's browse/detail/count re-verified under Inventory scope + Item detail), 11/15/16 (three admin screens as Settings tabs). Suite stays about the same size (~24 today). `npm run e2e` green is the gate.
4. Delete `Catalog.tsx`, its nav entry, and `catalog.detail` — last, after green (D9).
5. At sync: trim `product-catalog`, extend `inventory-view` (scope) and `item-detail` (category edit, count entry), reword `dashboard-shell` and `dashboard-smoke-tests` (D10). Deploy = existing jar-swap; no schema, printer, or money path touched; rollback = previous jar.

## Open Questions

- Whether the On-paper/All delivery picker on Item detail should be scoped to lots that still owe *this* product, or list all open deliveries as Catalog did — ships listing all open deliveries (reuses the existing call; the grid degrades cleanly for a lot that owes nothing). Non-blocking.
