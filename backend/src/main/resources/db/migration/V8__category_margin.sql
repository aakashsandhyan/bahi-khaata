-- V8 — target margin per category.
--
-- The target margin that suggests a price for an unpriced product resolves most-specific
-- first: a transient custom margin typed while pricing one product, otherwise this table,
-- otherwise the global default in SETTING.
--
-- A natural key on the category name, like SETTING and unlike everything else: two outlets
-- syncing their margins should collide on ELECTRONICS, because that is the same setting for
-- the same category rather than a conflict. The CHECK keeps it in step with the Category
-- enum, and the drift test holds the two together.
--
-- Runtime-editable, because the whole reason margins live in the database is a manager
-- changing one without a redeploy. Only categories that differ from the global default need a
-- row; an absent row falls through to it.
--
-- The seeded figures below are PLACEHOLDERS. They are not derived from Bachat Baazar trading
-- data and must be replaced with real numbers before they are relied on. The relative shape —
-- thinner on electronics, wider on gifting and decor — is a common retail pattern, not a
-- measurement of this business.

CREATE TABLE category_margin (
    category              TEXT    PRIMARY KEY
        CHECK (category IN ('HOME_ESSENTIALS', 'KITCHEN', 'ELECTRONICS',
                            'GIFTING', 'DECOR', 'FASHION')),
    target_margin_percent INTEGER NOT NULL
        CHECK (target_margin_percent >= 0 AND target_margin_percent < 100),
    created_at            TEXT    NOT NULL,
    updated_at            TEXT    NOT NULL
);

INSERT INTO category_margin (category, target_margin_percent, created_at, updated_at) VALUES
    ('HOME_ESSENTIALS', 30, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z'),
    ('KITCHEN',         30, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z'),
    ('ELECTRONICS',     20, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z'),
    ('GIFTING',         40, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z'),
    ('DECOR',           40, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z'),
    ('FASHION',         35, '2026-07-20T00:00:00.000Z', '2026-07-20T00:00:00.000Z');

-- Correcting the wording of the global default, which V2 described as the flat target for
-- every product. It is now the fallback used only where a category has no row of its own.
UPDATE setting
SET description = 'Gross margin used to suggest a selling price for an unpriced product, '
                  || 'where its category has no row in category_margin. A suggestion only — it '
                  || 'never becomes the price without a person accepting it. PLACEHOLDER: not '
                  || 'derived from Bachat Baazar trading figures.'
WHERE setting_key = 'pricing.target_margin_percent';
