# Design — Palletworks Selling + Dual UX

## Context

The sale engine is done and proven: complete-sale writes `sale` + FIFO ledger + GST, assigns `bill_no`, prints the receipt, reprints from the persisted sale. What's missing is everything around it: no drawer accountability (cash counted against nothing), a till UI that predates the design artifact, and a bill list (`Sales`) far thinner than the designed Invoices. Separately, the beta replaced the shop's known screens wholesale; the floor needs both UXes live at once, switchable per device, while trust transfers.

Reference: `openspec/changes/archive/2026-08-05-palletworks-foundation/reference/Palletworks.dc.html` — the design artifact; its Register/Checkout/Invoices sections are the visual contract.

## Goals / Non-Goals

**Goals:**
- Register sessions: open with counted float → cash in/out → close with count → over/short, operator-attached; sales link to the open session.
- Modern Checkout and Invoices screens per the artifact, on the existing engine.
- Whole-app Classic ⇄ Modern switch: one click, per-device, instant, presentation-only.
- Classic mode = the full pre-palletworks UX, including the three screens the nav fold deleted.

**Non-Goals:**
- No Returns, Customers, Analysis, Product-page screens (later phases of the artifact).
- No auth/PIN enforcement on register actions (the artifact's "void with PIN" waits for the roles change).
- No JavaFX terminal work.
- No change to sale recording, GST, or receipt rendering.

## Decisions

1. **`register_session` + `register_cash_movement`, not fields on sale.** A session row: register name ("Register 1"/"Register 2"), operator, `float_paise`, `opened_at`, `closed_at`, `counted_paise`, `over_short_paise`, status. Cash movements reference the session (IN/OUT, amount, note). `sale` gains nullable `register_session_id` — a sale rung with no session open (classic till, or before this ships) stays valid forever. *Rejected:* denormalizing drawer figures onto sale (session is the accountable unit, not the bill).
2. **Over/short is computed at close, then pinned.** Expected = float + cash-sales(session) + cash-in − cash-out; over/short = counted − expected, stored on the session at close so the figure survives later data corrections the way a bill does. One open session per register name, enforced.
3. **Session attach is the modern Checkout's job.** Modern Checkout requires an open session on its register before selling (the artifact's "Register 1 open" gate); classic Till keeps selling sessionless, unchanged. This is the behavioral line between the two UXes — everything else is presentation.
4. **Dual shell = two shell components over one screen registry.** `App.tsx` holds `uxMode` (`localStorage`, default modern) and mounts `ModernShell` (today's Sidebar shell) or `ClassicShell` (the restored sixteen-entry top bar). Screens shared by both (Unpacking, Prep, Pricing, Review, Reprint, Suppliers, admin configs, old Till) are the same component instances — no forks. `classic/` gains only what main had and the fold deleted: LotManagement, Receiving, Catalog, plus the retired `.topnav` styles scoped under a `.classic` root so they cannot leak into modern.
5. **The switch is a labeled control in both shells** — "Modern UX" in the classic top bar, "Classic UX" in the modern sidebar footer — flipping React state and writing localStorage; no reload. Phones are unaffected: they never had the classic nav, and their station screens are shared components.
6. **Invoices = designed list over the existing `/api/sales` reads** (summary list, `GET /api/sales/{billNo}`, reprint POST), plus filter-by-session for the register-close view. Classic's minimal Sales screen stays as-is in classic mode.
7. **Endpoints:** `GET /api/registers` (both registers + open session state), `POST /api/registers/{name}/open`, `/close`, `/cash-movements`; `complete-sale` request gains optional `registerSessionId`. All additive.
8. **Migrations continue at V50** (V49 taken by bin_and_price_history after the collision renumber; verify the ceiling again at apply time in case main moves).

## Risks / Trade-offs

- [Restored classic screens drift from main's copies as main evolves] → they are frozen at restore-time; classic is a bridge, not a maintained twin. Note the restore commit in each file header.
- [Two shells in one bundle grow it] → they share every screen; the delta is nav chrome. Measure at build; the jar has headroom.
- [Cash expected-vs-counted needs payment method on every session sale] → complete-sale already records method; only CASH feeds expected.
- [Register gate could block a sale in a rush if a session was never opened] → the gate offers "open register now" inline (float entry) rather than a dead end; classic Till also always exists.

## Migration Plan

Additive schema only; no backfill (old sales stay sessionless). Ship inside `release/full-update` — reaches the shop only when the beta line does. Rollback = revert; classic mode is itself the behavioral rollback.

## Open Questions

- Register names fixed at two ("Register 1"/"Register 2") or admin-editable? Shipping fixed pair; the table doesn't care.
- Does the register-close slip print on the receipt printer? Artifact hints yes; shipping screen-only summary first, print as a follow-up.
