-- V48 — best-effort classification of existing products into GST sub-categories.
--
-- Confident cases only. Categories that map to a single sub-category get it outright; KITCHEN is
-- split by material from the product name (metal 5% vs plastic/glass 18% vs appliance 18%), and
-- anything the heuristics can't place confidently is LEFT NULL — the till prompts for it once on
-- sale rather than guessing (an individually-wrong tax tag hides in every aggregate). Runs only on
-- rows still unclassified, so it is safe to re-run and never overwrites a hand-set value.

-- Categories with a single rate-group: assign it to every product in the category.
UPDATE product SET sub_category = 'TOYS_GENERAL'            WHERE category = 'TOYS'                AND sub_category IS NULL;
UPDATE product SET sub_category = 'WIRELESS_GENERAL'        WHERE category = 'WIRELESS'            AND sub_category IS NULL;
UPDATE product SET sub_category = 'PERSONAL_CARE_GENERAL'   WHERE category = 'PERSONAL_CARE'       AND sub_category IS NULL;
UPDATE product SET sub_category = 'ELECTRONICS_GENERAL'     WHERE category = 'ELECTRONICS'         AND sub_category IS NULL;
UPDATE product SET sub_category = 'HOME_ESSENTIALS_GENERAL' WHERE category = 'HOME_ESSENTIALS'     AND sub_category IS NULL;
UPDATE product SET sub_category = 'GARDEN_GENERAL'          WHERE category = 'GARDEN'              AND sub_category IS NULL;
UPDATE product SET sub_category = 'HOME_IMPROVEMENT_GENERAL' WHERE category = 'HOME_IMPROVEMENT'   AND sub_category IS NULL;
UPDATE product SET sub_category = 'MUSICAL_INSTRUMENTS_GENERAL' WHERE category = 'MUSICAL_INSTRUMENTS' AND sub_category IS NULL;
UPDATE product SET sub_category = 'GIFTING_GENERAL'         WHERE category = 'GIFTING'             AND sub_category IS NULL;
UPDATE product SET sub_category = 'DECOR_GENERAL'           WHERE category = 'DECOR'               AND sub_category IS NULL;
UPDATE product SET sub_category = 'LUGGAGE_GENERAL'         WHERE category = 'LUGGAGE'             AND sub_category IS NULL;

-- Apparel/footwear: default to the lower (5%) group; the above-₹2,500 items are moved to the HIGH
-- group by hand at pricing (the price-threshold rule is deliberately not automated here).
UPDATE product SET sub_category = 'FASHION_LOW'  WHERE category = 'FASHION'  AND sub_category IS NULL;
UPDATE product SET sub_category = 'FOOTWEAR_LOW' WHERE category = 'FOOTWEAR' AND sub_category IS NULL;

-- KITCHEN — split by material from the name. Metal utensils are 5%; plastic/glass and appliances
-- are 18%. Order matters: appliances first (a "steel kettle" is an appliance, not a utensil), then
-- plastic/glass, then metal. Anything unmatched stays NULL for the till to resolve.
UPDATE product SET sub_category = 'KITCHEN_APPLIANCE'
WHERE category = 'KITCHEN' AND sub_category IS NULL
  AND (lower(name) LIKE '%kettle%' OR lower(name) LIKE '%stove%' OR lower(name) LIKE '%mixer%'
       OR lower(name) LIKE '%grinder%' OR lower(name) LIKE '%toaster%' OR lower(name) LIKE '%fan%'
       OR lower(name) LIKE '%ups%' OR lower(name) LIKE '%induction%' OR lower(name) LIKE '%heater%'
       OR lower(name) LIKE '%appliance%' OR lower(name) LIKE '%electric%');

UPDATE product SET sub_category = 'KITCHEN_PLASTIC'
WHERE category = 'KITCHEN' AND sub_category IS NULL
  AND (lower(name) LIKE '%plastic%' OR lower(name) LIKE '%glass%' OR lower(name) LIKE '%ceramic%'
       OR lower(name) LIKE '%opal%' OR lower(name) LIKE '%melamine%' OR lower(name) LIKE '%acrylic%');

UPDATE product SET sub_category = 'KITCHEN_METAL'
WHERE category = 'KITCHEN' AND sub_category IS NULL
  AND (lower(name) LIKE '%steel%' OR lower(name) LIKE '%iron%' OR lower(name) LIKE '%aluminium%'
       OR lower(name) LIKE '%aluminum%' OR lower(name) LIKE '%inox%' OR lower(name) LIKE '%brass%'
       OR lower(name) LIKE '%copper%' OR lower(name) LIKE '%utensil%' OR lower(name) LIKE '%kadai%'
       OR lower(name) LIKE '%kadhai%' OR lower(name) LIKE '%tawa%' OR lower(name) LIKE '%cooker%');
