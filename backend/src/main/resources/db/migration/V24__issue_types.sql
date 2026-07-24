-- V24 — the kinds of work a not-quite-sellable item needs, and which apply to which department.
--
-- Liquidation goods arrive imperfect but often fixable: an induction that works but is filthy, a
-- shirt that is perfect but creased, a kettle sound but missing its base. Such an item is neither
-- sold as cheaper seconds nor put on the shelf as it is — it is set aside to be prepared, and made
-- sellable at full price once the work is done. The work has a kind, and the kind depends on the
-- department: dry-cleaning is for clothes, a rebuild for appliances, a polish for footwear.
--
-- These are data, not an enum, for the same reason `Category` was made a table — the shop's needs
-- do not fit a list fixed in code, and a new kind of work must not need a release to add. A master
-- list of issue types, and a mapping saying which are offered for each department.

CREATE TABLE issue_type (
    code  TEXT PRIMARY KEY,
    label TEXT NOT NULL
);

CREATE TABLE category_issue_type (
    category_code   TEXT NOT NULL,
    issue_type_code TEXT NOT NULL REFERENCES issue_type (code),
    PRIMARY KEY (category_code, issue_type_code)
);

INSERT INTO issue_type (code, label) VALUES
    ('CLEAN',       'Clean'),
    ('REPAIR',      'Repair'),
    ('REBUILD',     'Rebuild / parts'),
    ('TEST',        'Test'),
    ('REPACK',      'Repack'),
    ('DRYCLEAN',    'Dry-clean'),
    ('WASH',        'Wash'),
    ('MEND',        'Mend / stitch'),
    ('IRON',        'Iron'),
    ('POLISH',      'Polish'),
    ('SOLE_REPAIR', 'Sole / heel repair');

-- The locked grid: which kinds of work each department offers.
INSERT INTO category_issue_type (category_code, issue_type_code) VALUES
    ('KITCHEN', 'CLEAN'), ('KITCHEN', 'REPAIR'), ('KITCHEN', 'REBUILD'), ('KITCHEN', 'TEST'), ('KITCHEN', 'REPACK'),
    ('WIRELESS', 'TEST'), ('WIRELESS', 'REPAIR'), ('WIRELESS', 'CLEAN'), ('WIRELESS', 'REPACK'),
    ('FASHION', 'DRYCLEAN'), ('FASHION', 'WASH'), ('FASHION', 'MEND'), ('FASHION', 'IRON'), ('FASHION', 'REPACK'),
    ('FOOTWEAR', 'CLEAN'), ('FOOTWEAR', 'POLISH'), ('FOOTWEAR', 'SOLE_REPAIR'), ('FOOTWEAR', 'REPACK'),
    ('HOME_ESSENTIALS', 'CLEAN'), ('HOME_ESSENTIALS', 'REBUILD'), ('HOME_ESSENTIALS', 'REPACK'),
    ('PERSONAL_CARE', 'REPACK'), ('PERSONAL_CARE', 'CLEAN'),
    ('GARDEN', 'CLEAN'), ('GARDEN', 'REPAIR'), ('GARDEN', 'REPACK');
