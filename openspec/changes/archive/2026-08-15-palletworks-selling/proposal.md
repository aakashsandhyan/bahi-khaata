# Palletworks Selling + Dual UX

## Why

The Palletworks design artifact defines a selling suite — a Register with drawer sessions, a designed Checkout, an Invoices screen — that was deferred when phases 1–4 shipped the back office; the web till today is the pre-design Checkout kept alive behind a `#till` hash. Meanwhile the beta replaced screens the shop knows, and staff need to trade through a transition, not across a cliff: the old UX must stay fully usable beside the new one, switchable in one click, until the new one has earned the floor's trust.

## What Changes

- **Register**: drawer sessions as a first-class model — open a register with a counted float, tie it to an operator, record cash in/out against it, close with a counted drawer and an over/short figure. Sales made while a register is open attach to that session. New backend model + migrations; the design's "Register 1 open" state drives the screen.
- **Checkout (modern)**: the designed point-of-sale screen from the artifact — scan-first, cart, pay via the existing complete-sale engine (bill number, GST, receipt print). The old Till component is untouched.
- **Invoices**: the designed bill-history screen — list with method/items/total, search by bill number, per-row reprint — replacing the minimal Sales list *in the modern UX only*.
- **Whole-app UX switch**: one visible control flips the entire dashboard between **Classic** (the pre-palletworks flat top-bar nav and its screens) and **Modern** (the palletworks shell). Per-device, persisted like the operator name, effective immediately — no reload dance, no admin setting buried anywhere.
- **Classic screens restored**: LotManagement, Receiving, and Catalog return from main (they were deleted in the nav fold) and mount only under Classic; Classic nav is the sixteen-entry top bar as the shop remembers it, including Till and the admin config screens as separate entries.
- Both UXes share one backend, one database, one set of API calls — the switch changes presentation, never behavior. A sale rung in Classic shows in Modern's Invoices and vice versa.

## Capabilities

### New Capabilities
- `register-sessions`: drawer lifecycle — open with float, cash movements, close with count and over/short; sales attach to the open session.
- `selling-screens`: the modern Checkout and Invoices screens per the design artifact.
- `dual-ux-shell`: the whole-app Classic ⇄ Modern switch — what each mode shows, how the choice persists per device, and the guarantee that both modes drive the same backend.

### Modified Capabilities

(none — existing capability requirements stand; both UXes consume them unchanged)

## Impact

- Backend: new `register_session` (+ cash movement) tables and endpoints; `sale` gains an optional session reference. Existing checkout/sales/GST engines untouched.
- Frontend: `App.tsx` grows the mode switch and mounts one of two shells; classic screens restored from main into a `classic/` home; modern Sidebar gains Register / Checkout / Invoices entries.
- Migrations continue after V49.
- Old Till stays reachable in both modes (`#till` keeps working); nothing the shop uses today is removed.
