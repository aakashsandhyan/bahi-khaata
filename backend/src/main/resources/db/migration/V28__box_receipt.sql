CREATE TABLE box_receipt (
  id CHAR(36) NOT NULL PRIMARY KEY,
  lot_id CHAR(36) NOT NULL,
  manifest_carton_id TEXT NOT NULL,
  box_state TEXT NOT NULL DEFAULT 'EXPECTED',
  received_at TEXT NULL,
  rejected_reason TEXT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (lot_id) REFERENCES lot(id) ON DELETE CASCADE
);

CREATE INDEX idx_box_receipt_lot_id ON box_receipt(lot_id);
CREATE INDEX idx_box_receipt_lot_carton ON box_receipt(lot_id, manifest_carton_id);
CREATE INDEX idx_box_receipt_state ON box_receipt(box_state);
