-- V47 — regular-dealer GST: rate-group vocabulary, effective-dated rates, and the tax snapshot.
--
-- The shop is a regular dealer but shipped composition-wrong (Bill of Supply, no tax) while the
-- till added 18% on top of an MRP-inclusive price. This lays the schema for extracting GST
-- inclusively at per-sub_category rates and freezing it onto each sale.
--
-- Rates are held in BASIS POINTS (18% = 1800), an integer so odd slabs (0.25% = 25, 3% = 300) are
-- exact on SQLite — the exact form of a "decimal rate". Percent = basis_points / 100.

-- The rate-bearing classification. A category is too coarse (KITCHEN holds 5% metal and 18%
-- plastic/appliances), so a finer, controlled vocabulary carries the rate. Parent = its category.
CREATE TABLE sub_category (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    category    TEXT NOT NULL REFERENCES category (code),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- Effective-dated rate per sub_category. Exactly one active row per sub_category (partial unique
-- index below); a rate change closes the old row and opens a new one — history is never edited.
CREATE TABLE gst_rate (
    id                CHAR(36) PRIMARY KEY,
    sub_category      TEXT     NOT NULL REFERENCES sub_category (code),
    gst_basis_points  INTEGER  NOT NULL CHECK (gst_basis_points >= 0 AND gst_basis_points <= 10000),
    effective_from    TEXT     NOT NULL,
    effective_to      TEXT,
    is_active         BOOLEAN  NOT NULL DEFAULT true,
    created_at        TEXT     NOT NULL,
    updated_at        TEXT     NOT NULL
);

-- The invariant: at most one active rate per sub_category, enforced by the database itself.
CREATE UNIQUE INDEX idx_gst_rate_active ON gst_rate (sub_category) WHERE is_active = 1;

-- Products carry the rate-group. Nullable: an unclassified product is resolved at the till.
ALTER TABLE product ADD COLUMN sub_category TEXT REFERENCES sub_category (code);

-- Tax snapshot on the cart line (frozen at scan) and the sale line (frozen at completion), so a
-- later rate change never moves the tax on a rung line.
ALTER TABLE cart_line ADD COLUMN gst_basis_points INTEGER;
ALTER TABLE cart_line ADD COLUMN tax_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sale_line ADD COLUMN gst_basis_points INTEGER;
ALTER TABLE sale_line ADD COLUMN tax_paise BIGINT NOT NULL DEFAULT 0;

-- Invoice-level breakdown frozen on the sale (CGST = SGST = tax/2; taxable = subtotal − tax).
ALTER TABLE sale ADD COLUMN cgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sale ADD COLUMN sgst_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sale ADD COLUMN taxable_paise BIGINT NOT NULL DEFAULT 0;

-- The receipt becomes a Tax Invoice. Update the single settings row (V44 seeded it) and note that
-- fresh installs run this after V43/V44, so they too land on the Tax Invoice defaults.
UPDATE bill_settings
SET bill_title  = 'Tax Invoice',
    declaration = '',
    updated_at  = strftime('%Y-%m-%dT%H:%M:%fZ', 'now');

-- Global fallback rate for an unclassified product: 18% (standard slab).
INSERT INTO setting (setting_key, setting_value, description) VALUES
    ('gst.default_basis_points', '1800',
     'Fallback GST rate (basis points; 1800 = 18%) for a product with no sub_category.');

-- Seed the starting vocabulary + a placeholder active rate for each. These percentages are
-- indicative — the CA confirms the real per-sub_category rates before go-live, via the admin
-- screen (a data edit, not a code change). Whole-material splits: metal utensils and toys 5%,
-- everything standard 18%; apparel/footwear seeded at 5% with the above-₹2,500 items overridden
-- by hand at pricing.
INSERT INTO sub_category (code, name, category, created_at, updated_at) VALUES
    ('KITCHEN_METAL',       'Kitchen — metal utensils',   'KITCHEN',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('KITCHEN_PLASTIC',     'Kitchen — plastic/glass',    'KITCHEN',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('KITCHEN_APPLIANCE',   'Kitchen — appliances',       'KITCHEN',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('FASHION_LOW',         'Apparel — up to ₹2,500',     'FASHION',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('FASHION_HIGH',        'Apparel — above ₹2,500',     'FASHION',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('FOOTWEAR_LOW',        'Footwear — up to ₹2,500',    'FOOTWEAR',        '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('FOOTWEAR_HIGH',       'Footwear — above ₹2,500',    'FOOTWEAR',        '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('TOYS_GENERAL',        'Toys',                       'TOYS',            '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('WIRELESS_GENERAL',    'Wireless accessories',       'WIRELESS',        '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('PERSONAL_CARE_GENERAL','Personal care',             'PERSONAL_CARE',   '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('ELECTRONICS_GENERAL', 'Electronics',                'ELECTRONICS',     '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('HOME_ESSENTIALS_GENERAL','Home essentials',         'HOME_ESSENTIALS', '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('GARDEN_GENERAL',      'Lawn and garden',            'GARDEN',          '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('HOME_IMPROVEMENT_GENERAL','Home improvement',       'HOME_IMPROVEMENT','2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('MUSICAL_INSTRUMENTS_GENERAL','Musical instruments', 'MUSICAL_INSTRUMENTS','2026-08-09T00:00:00.000Z','2026-08-09T00:00:00.000Z'),
    ('GIFTING_GENERAL',     'Gifting',                    'GIFTING',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('DECOR_GENERAL',       'Decor',                      'DECOR',           '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z'),
    ('LUGGAGE_GENERAL',     'Luggage',                    'LUGGAGE',         '2026-08-09T00:00:00.000Z', '2026-08-09T00:00:00.000Z');

INSERT INTO gst_rate (id, sub_category, gst_basis_points, effective_from, effective_to, is_active, created_at, updated_at)
SELECT
    lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' ||
          substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))),
    code,
    CASE code
        WHEN 'KITCHEN_METAL' THEN 500
        WHEN 'TOYS_GENERAL' THEN 500
        WHEN 'FASHION_LOW' THEN 500
        WHEN 'FOOTWEAR_LOW' THEN 500
        ELSE 1800
    END,
    '2026-08-09', NULL, true,
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM sub_category;
