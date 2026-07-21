-- V11 — what the goods sold for online, kept as an input to pricing.
--
-- A liquidation manifest priced off retail states what each line sold for on Amazon. That
-- figure decided the line's share of the lot cost and was then discarded, which threw away
-- the only signal we had about what the goods are worth. Nothing in the system could answer
-- "what did this sell for online" — a fair question when setting a shelf price for something
-- nobody in the shop has seen before.
--
-- Three things it is NOT:
--
--   1. It is not an MRP. MRP is the printed legal ceiling and selling above it is unlawful;
--      a marketplace price is one seller's asking price on one day. V9 made MRP nullable
--      precisely so the two would stop being conflated, and this column must not undo that.
--      MRP remains the only gate on sellability — see ProductSellabilityTest.
--
--   2. It is not a cost. Sheets priced at supplier-cost-plus carry no market figure at all,
--      so for those products this stays NULL and should. Writing a supplier's cost here
--      would be the same conflation one floor down.
--
--   3. It is not durable. A price observed in July 2026 is not evidence in 2027, which is
--      why the date it was seen is stored beside it. A number without its date outlives its
--      truth and no one can tell.
--
-- Latest observation wins, so this keeps no history. Deliberate for now: the pricing screen
-- wants one number, cheaply. When the manifest's expected-lines table arrives with
-- goods-in-from-manifest it will retain each consignment's raw figure, and a full
-- observations table can be built from those if the trend ever proves worth having.

ALTER TABLE product ADD COLUMN online_price_paise BIGINT;

-- A CHECK rather than a lookup table: unlike categories, which arrived from a supplier's
-- taxonomy and immediately outgrew a fixed list, the marketplaces we buy against are few and
-- change only by deliberate business decision. If that stops being true, this becomes a table
-- the same way category did.
ALTER TABLE product ADD COLUMN online_price_source TEXT
    CHECK (online_price_source IN ('AMAZON', 'FLIPKART'));

ALTER TABLE product ADD COLUMN online_price_observed_on TEXT;
