# Customer Capture at Checkout

## Why

Every sale today is anonymous. The shop's policy is to ask every customer for their name and mobile at the counter — the ones who decline are walk-ins — but the till has nowhere to put the answer. The Palletworks artifact already envisions the destination (a Customers screen with repeat-share stats, a lapsed-60-days list "worth a message when lots land"), and two future integrations need the data captured now: offers need purchase history per person, and e-invoicing needs a buyer block on the sale.

## What Changes

- **Customer record**: name + mobile, mobile as the identity key — one customer per number, the counter asks for the number first. Migration V52; `sale` gains a nullable `customer_id`.
- **Capture as a step in the payment flow**: Take payment first presents the customer ask — key a mobile, an existing customer autofills and attaches, a new pair saves and attaches; one tap on **Walk-in** declines and proceeds. Asking is the default every sale; declining is one action; nothing ever blocks the sale.
- **Customers screen** (Back office, per the artifact): the stats strip (people on file, repeat share of revenue, average repeat basket vs walk-in, lapsed 60+ days), the customer list (name, tag, phone, visits, spent, average basket, category likes, last visit), and a per-customer visit history. All figures derived from the customer record and the sale link — no denormalized stats.
- **Future hooks named, not built**: the lapsed list is the offers audience; name + mobile on the sale is the e-invoice buyer block. No offer sending, no IRP integration, no GSTIN/B2B fields yet.
- Classic till stays frozen — capture is a modern-checkout feature.

## Capabilities

### New Capabilities
- `customer-records`: the customer identity — mobile-keyed lookup, create, attach to a sale; derived per-customer history and shop-level stats.

### Modified Capabilities
- `selling-screens`: the modern checkout's payment flow gains the always-ask customer step with the one-tap walk-in decline; the cart header names the attached customer in place of "Walk-in customer".

## Impact

- Backend: `customer` table (V52), nullable `sale.customer_id`, lookup/create/attach endpoints, stats + history queries. Existing sale engine untouched beyond the link.
- Frontend: payment-flow customer step in `CheckoutModern`, Customers screen + sidebar entry (Back office), cart header shows the attached name.
- PII note: mobile numbers on an unlocked dashboard — the list masks numbers, full number on the customer's own record; the future role gate inherits this surface.
