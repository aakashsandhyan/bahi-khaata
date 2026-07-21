-- V10 — categories become data rather than code.
--
-- They were an enum, on the reasoning that a shop's departments are stable. The first real
-- consignment disproved that: of 3,583 units, a third fell outside the six values — wireless
-- accessories, footwear, personal care, lawn and garden — with a tail of single units in
-- home improvement, outdoors, musical instruments and luggage. A supplier's mix is not a
-- fixed list, and every new one would otherwise mean editing code and writing a migration.
--
-- So a category is now a row. Adding one is a data insert, which is what its behaviour
-- always was. The cost is the compile-time safety an enum gave; the foreign key keeps the
-- set governed, so a product still cannot carry a category that does not exist.
--
-- `amazon_product_line` records the supplier's own code for the same thing, so an imported
-- manifest can be mapped without anyone matching names by hand. It is nullable: our
-- categories are ours, and not all of them will ever appear in someone else's taxonomy.

CREATE TABLE category (
    code                TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    amazon_product_line TEXT UNIQUE,
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL
);

INSERT INTO category (code, name, amazon_product_line, created_at, updated_at) VALUES
    ('HOME_ESSENTIALS',     'Home essentials',      'gl_home',                       '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('KITCHEN',             'Kitchen',              'gl_kitchen',                    '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('ELECTRONICS',         'Electronics',          NULL,                            '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('GIFTING',             'Gifting',              NULL,                            '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('DECOR',               'Decor',                NULL,                            '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('FASHION',             'Fashion',              'gl_apparel',                    '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('WIRELESS',            'Wireless accessories', 'gl_wireless_accessory',         '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('FOOTWEAR',            'Footwear',             'gl_shoes',                      '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('PERSONAL_CARE',       'Personal care',        'gl_personal_care_appliances',   '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('GARDEN',              'Lawn and garden',      'gl_lawn_and_garden',            '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('HOME_IMPROVEMENT',    'Home improvement',     'gl_home_improvement',           '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('OUTDOORS',            'Outdoors',             'gl_outdoors',                   '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('MUSICAL_INSTRUMENTS', 'Musical instruments',  'gl_musical_instruments',        '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('LUGGAGE',             'Luggage',              'gl_luggage',                    '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z');

-- product.category loses its CHECK and gains a foreign key. SQLite cannot alter either, so
-- the table is rebuilt; safe here because it runs before any product exists.
CREATE TABLE product_rebuilt (
    id                    CHAR(36)  PRIMARY KEY,
    name                  TEXT      NOT NULL,
    category              TEXT      NOT NULL REFERENCES category (code),
    selling_price_paise   BIGINT,
    hsn_code              TEXT,
    attributes            CLOB,
    price_review_flagged  BOOLEAN   NOT NULL DEFAULT 0,
    created_at            TEXT      NOT NULL,
    updated_at            TEXT      NOT NULL
);

INSERT INTO product_rebuilt SELECT * FROM product;
DROP TABLE product;
ALTER TABLE product_rebuilt RENAME TO product;

-- The same for the per-category margins, which were keyed by the enum's values.
CREATE TABLE category_margin_rebuilt (
    category              TEXT    PRIMARY KEY REFERENCES category (code),
    target_margin_percent INTEGER NOT NULL
        CHECK (target_margin_percent >= 0 AND target_margin_percent < 100),
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL
);

INSERT INTO category_margin_rebuilt SELECT * FROM category_margin;
DROP TABLE category_margin;
ALTER TABLE category_margin_rebuilt RENAME TO category_margin;

-- The new categories have no margin of their own yet, so they fall through to the global
-- default until someone sets one. PLACEHOLDERS, as with the others: the shape is a common
-- retail pattern, not a measurement of this business.
INSERT INTO category_margin (category, target_margin_percent, created_at, updated_at) VALUES
    ('WIRELESS',            30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('FOOTWEAR',            35, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('PERSONAL_CARE',       30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('GARDEN',              30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('HOME_IMPROVEMENT',    30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('OUTDOORS',            30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('MUSICAL_INSTRUMENTS', 30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z'),
    ('LUGGAGE',             30, '2026-07-21T00:00:00.000Z', '2026-07-21T00:00:00.000Z');
