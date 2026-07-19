-- V2 — business configuration.
--
-- Values a shop manager might reasonably want to change, held in the database so
-- changing one does not mean editing a file and restarting the backend mid-trading-day.
-- Infrastructure configuration — database path, ports, base URLs — stays in properties.
-- See design decision 16.
--
-- Natural key, deliberately not a UUID, which every other table uses. Two outlets syncing
-- settings should collide on a shared key: that is the same setting, not two settings.
-- A generated identifier would make identical configuration look like a conflict.
--
-- `setting_key` and `setting_value` rather than `key` and `value` because `value` is
-- reserved in SQL:2016. SQLite tolerates it; Postgres is the stated future and would not.

CREATE TABLE setting (
    setting_key   TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    description   TEXT NOT NULL
);

INSERT INTO setting (setting_key, setting_value, description) VALUES
    ('pricing.margin_review_threshold_points',
     '5',
     'Percentage points of gross margin loss that flags a product for price review when '
     || 'stock arrives at a higher cost. Compared against the margin at the most recent '
     || 'prior batch cost. Flagging never blocks a sale.'),

    ('pricing.target_margin_percent',
     '30',
     'Gross margin used to suggest a selling price for an unpriced product. A suggestion '
     || 'only — it never becomes the price without a person accepting it. PLACEHOLDER: '
     || 'not derived from Bachat Baazar trading figures.'),

    ('checkout.cart_expiry_minutes',
     '120',
     'Inactivity after which an abandoned cart is discarded. Long enough to survive an '
     || 'interrupted sale, short enough that forgotten carts do not accumulate. '
     || 'PLACEHOLDER: should be set from how long a real interrupted sale takes to resume.');
