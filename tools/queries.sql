-- Handy read-only queries against backend/data/bahi-khaata.db
--   sqlite3 backend/data/bahi-khaata.db < tools/queries.sql
--
-- Nothing here writes. The stock ledger refuses UPDATE and DELETE at the database
-- level anyway, but the rest of the tables do not, so take care by hand.

.headers on
.mode column

SELECT '--- where each delivery stands ---' AS "";
SELECT p.category                                        AS delivery,
       COUNT(DISTINCT b.id)                              AS cartons,
       SUM(CASE WHEN b.finished_at IS NOT NULL THEN 1 END) AS done,
       SUM(e.quantity_expected)                          AS expected,
       SUM(e.quantity_counted)                           AS found,
       MAX(l.state)                                      AS state
FROM lot l
JOIN expected_line e ON e.lot_id = l.id
JOIN box b           ON b.id = e.box_id
JOIN product p       ON p.id = e.product_id
GROUP BY l.id ORDER BY SUM(e.quantity_expected) DESC;

SELECT '--- items counted but with no MRP, so unsellable ---' AS "";
SELECT substr(p.name, 1, 50) AS item,
       ba.quantity_received  AS units,
       ba.condition          AS condition,
       COALESCE(printf('%.2f', p.online_price_paise / 100.0), '—') AS online_price
FROM batch ba JOIN product p ON p.id = ba.product_id
WHERE ba.mrp_paise IS NULL;

SELECT '--- shortfalls and surpluses against the manifest ---' AS "";
SELECT substr(p.name, 1, 44)                     AS item,
       e.quantity_expected                       AS expected,
       e.quantity_counted                        AS found,
       e.quantity_counted - e.quantity_expected  AS difference
FROM expected_line e JOIN product p ON p.id = e.product_id
WHERE e.quantity_counted > 0
  AND e.quantity_counted <> e.quantity_expected;

SELECT '--- real barcodes learnt while unpacking ---' AS "";
SELECT b.code AS scanned_code, substr(p.name, 1, 50) AS item
FROM barcode b JOIN product p ON p.id = b.product_id
WHERE b.origin = 'MANUFACTURER';

SELECT '--- does every closed delivery reconcile to what was paid? ---' AS "";
SELECT l.supplier,
       printf('%.2f', l.amount_paid_paise / 100.0)        AS paid,
       printf('%.2f', SUM(ba.allocated_total_paise)/100.0) AS allocated,
       CASE WHEN l.amount_paid_paise = SUM(ba.allocated_total_paise)
            THEN 'exact' ELSE 'OFF' END                    AS reconciles
FROM lot l JOIN batch ba ON ba.lot_id = l.id
WHERE l.state = 'CLOSED' GROUP BY l.id;
