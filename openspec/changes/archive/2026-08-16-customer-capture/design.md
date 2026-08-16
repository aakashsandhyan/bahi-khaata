# Design — Customer Capture at Checkout

## Context

The sale engine is stable (bill numbers, GST, sessions, custom lines); the counter policy is new: ask every customer for name + mobile, walk-in only on decline. The Palletworks artifact fixes the destination UI (Customers screen with stats, list, visit history). Nothing about a sale changes except an optional link to who bought.

## Goals / Non-Goals

**Goals:**
- Mobile-keyed customer identity; capture inside the payment flow with one-tap walk-in decline.
- Sale carries the customer link; per-customer history and shop stats derive from it.
- Customers screen per the artifact.

**Non-Goals:**
- No offers/messaging, no e-invoicing/IRP, no GSTIN/B2B fields, no dedupe/merge tooling, no classic-till capture, no customer editing beyond the counter's name-refresh.

## Decisions

1. **Mobile is the key, normalized to 10 digits.** Input strips `+91`, spaces, dashes; a valid key is 10 digits starting 6–9 (the Indian mobile plan). Stored normalized, unique. *Rejected:* free-text phone (defeats lookup — the whole point is one customer per number).
2. **Upsert by mobile, latest name wins.** `POST /api/customers {name, mobile}`: unknown mobile creates; known mobile returns the existing customer, updating the name when a non-blank different one is keyed — counters hear corrections ("it's Meera, not Mira") and the freshest utterance is the best record. *Rejected:* immutable name (stale records nobody can fix at the counter).
3. **The link rides the complete-sale request, not the cart.** `CompleteSaleRequest` gains optional `customerId`, validated at the API edge like `registerSessionId`; `sale.customer_id` is nullable (V52, additive `ALTER TABLE` — no rebuild). Walk-in = null, forever valid. *Rejected:* customer on the cart (schema churn for a value only the completed sale needs).
4. **The payment flow becomes two steps.** Take payment → **customer step**: mobile field autofocused; ten digits trigger lookup — found shows the name to confirm, unknown reveals the name field; **Walk-in** is a single always-visible button that skips to step two; → **method step** (CASH/UPI/CARD) unchanged. The ask is structurally default; declining is one tap; the sale is never blocked — including when the lookup itself errors (network blip = proceed as walk-in).
5. **All figures derived at read time.** Stats strip and per-customer rows are SQL over `customer × sale (× sale_line)`: repeat share (revenue of 2+-visit customers over all revenue), average repeat basket vs walk-in average, lapsed (last sale older than 60 days), likes (top categories by spend from that customer's lines). No counters, no denormalized columns — at this shop's scale the queries are trivial, and stored aggregates drift. *Rejected:* stats columns on customer.
6. **Masking rule:** the customer list shows the last 4 digits (`•••• 5120`); the full number renders only on the customer's own detail view. The future role gate inherits exactly this seam.
7. **Cart header names the catch.** Once attached (client-side, pre-payment), "Walk-in customer" becomes the customer's name — the artifact's header — and clearing the cart clears the attachment.

## Risks / Trade-offs

- [Counter mistypes a mobile → wrong identity accrues history] → confirm step shows the found name before attach; wrong-name correction is the name-refresh path; true merges are out of scope until they hurt.
- [Always-ask slows the queue] → the ask is one field already focused, and Walk-in is one tap; measure at the counter before adding shortcuts.
- [PII on an open dashboard] → masking rule above; full export surfaces wait for the role gate.

## Migration Plan

V52: `customer` table + `ALTER TABLE sale ADD COLUMN customer_id` + index. Additive only; rollback = revert jar (column sits inert). Ships to beta first, prod on the usual promote.

## Open Questions

- Landline/other formats at the counter (10-digit rule refuses them) — acceptable for now; the shop's customers carry mobiles.
