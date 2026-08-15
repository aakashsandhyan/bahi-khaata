# Tasks — Customer Capture at Checkout

## 1. Customer records (backend)

- [x] 1.1 Migration V52 (verify ceiling first): `customer` table (normalized unique mobile) + nullable `sale.customer_id` + indexes — additive only
- [x] 1.2 `Customer` entity + repository; mobile normalization (strip +91/punctuation → ten digits starting 6–9, else refused)
- [x] 1.3 `CustomerService`: lookup by mobile, upsert (create unknown / return known with name refresh on a different non-blank name)
- [x] 1.4 Endpoints: `GET /api/customers/by-mobile/{mobile}`, `POST /api/customers`; contracts
- [x] 1.5 Unit tests: normalization variants resolve to one customer, non-mobile refused, name refresh sticks, blank name never clobbers

## 2. Sale linkage

- [x] 2.1 `CompleteSaleRequest` gains optional `customerId`, validated at the API edge; recorded on the sale; null = walk-in
- [x] 2.2 Tests: attached sale references the customer; walk-in and pre-capture history stay null and valid

## 3. Checkout payment flow

- [x] 3.1 Customer step ahead of the method step: focused mobile field, auto-lookup at ten digits, found-name confirm, unknown reveals name field, always-one-tap Walk-in; lookup failure proceeds as walk-in
- [x] 3.2 Cart header shows the attached name in place of "Walk-in customer"; clearing the cart clears the attachment
- [x] 3.3 e2e: attach a known customer through the step; walk-in path; header follows attachment

## 4. Customers screen

- [x] 4.1 Stats + history queries (derived only): per-customer visits/spent/avg/likes/last, shop strip (people on file, repeat share, avg repeat basket vs walk-in, lapsed 60+); endpoints + contracts
- [x] 4.2 Customers screen per the artifact: stats strip, list (masked mobiles, last four), customer detail with full number + visit history
- [x] 4.3 Sidebar entry under Back office; screenMeta
- [x] 4.4 Tests: stats math (repeat share, lapsed boundary), masking in the list payload or view

## 5. Verify

- [x] 5.1 Full backend suite green
- [x] 5.2 e2e suite green (including the new customer-step specs)
- [ ] 5.3 Browser walk on the running app: sell to a new customer, resell by the same mobile, see both visits on their record and the stats move
- [x] 5.4 `openspec validate customer-capture`
