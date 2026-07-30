-- V37 — pin cost onto goods received before the costing model changed.
--
-- Cost is now pinned per product as it is received (see GoodsInCounting), not apportioned when
-- the lot closes. Stock counted before that change sits costed at nothing: its batches carry no
-- allocated cost, so nothing in those lots can be priced on margin though it is on the floor.
--
-- The cost was always derivable from what is already stored, and this settles it the same way
-- the counting path now does. Each category is its own lot, so a lot has one rate: the amount
-- paid (with freight, part of landed cost) over the total value its lines were expected to hold.
-- A batch's cost is its product's stated value scaled by that rate — a fraction of the online
-- price on a returns lot, a markup over the supplier's cost on a supply lot. Computed over
-- expected quantities, so a shortfall does not raise the survivors' cost.
--
-- Only touches batches left uncosted. A batch already costed — pinned by the counting path after
-- this change, or costed some other way — is not re-touched. Unusable scrap is never stock and
-- is left alone. A lot whose lines state no value at all cannot be costed and is skipped; its
-- goods stay uncosted, to be hand-priced, exactly as a surplus is.
--
-- Per-unit rounding means the pinned costs of a lot may sum a few paise off what was paid; that
-- surfaces in the amount-paid cross-check as a small difference, never absorbed into the goods.

UPDATE batch
SET allocated_unit_cost_paise = CAST(ROUND(
        (SELECT el.stated_value_paise
           FROM expected_line el
          WHERE el.lot_id = batch.lot_id
            AND el.product_id = batch.product_id
            AND el.stated_value_paise IS NOT NULL
          LIMIT 1)
        * (SELECT (l.amount_paid_paise + l.freight_paise) * 1.0 FROM lot l WHERE l.id = batch.lot_id)
        / (SELECT SUM(el2.stated_value_paise * el2.quantity_expected)
             FROM expected_line el2
            WHERE el2.lot_id = batch.lot_id
              AND el2.stated_value_paise IS NOT NULL)
    ) AS INTEGER),
    allocated_total_paise = CAST(ROUND(
        (SELECT el.stated_value_paise
           FROM expected_line el
          WHERE el.lot_id = batch.lot_id
            AND el.product_id = batch.product_id
            AND el.stated_value_paise IS NOT NULL
          LIMIT 1)
        * (SELECT (l.amount_paid_paise + l.freight_paise) * 1.0 FROM lot l WHERE l.id = batch.lot_id)
        / (SELECT SUM(el2.stated_value_paise * el2.quantity_expected)
             FROM expected_line el2
            WHERE el2.lot_id = batch.lot_id
              AND el2.stated_value_paise IS NOT NULL)
    ) AS INTEGER) * (quantity_received - quantity_damaged),
    cost_basis = 'PINNED'
WHERE allocated_unit_cost_paise IS NULL
  AND quantity_received > 0
  AND condition <> 'UNUSABLE'
  AND EXISTS (
        SELECT 1 FROM expected_line el
         WHERE el.lot_id = batch.lot_id
           AND el.product_id = batch.product_id
           AND el.stated_value_paise IS NOT NULL)
  AND (SELECT SUM(el2.stated_value_paise * el2.quantity_expected)
         FROM expected_line el2
        WHERE el2.lot_id = batch.lot_id
          AND el2.stated_value_paise IS NOT NULL) > 0;
