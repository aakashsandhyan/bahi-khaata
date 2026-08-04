// Typed mirror of e2e/sql/R__e2e_seed.sql — every fixed id, barcode, and name a test references.
// Keep this file and the seed migration in lockstep: a literal changed in one and not the other
// is exactly the drift this file exists to prevent.

export const seed = {
  supplier: {
    id: 'e2e00001-0000-4000-8000-000000000001',
    name: 'E2E Test Supplier',
  },

  lot: {
    id: 'e2e00001-0000-4000-8000-000000000002',
    receivedOn: '2026-08-01',
  },

  boxReceipt: {
    received: {
      id: 'e2e00001-0000-4000-8000-000000000003',
      manifestCartonId: 'E2E-BOX-001',
      state: 'RECEIVED',
    },
    expected: {
      id: 'e2e00001-0000-4000-8000-000000000004',
      manifestCartonId: 'E2E-BOX-002',
      state: 'EXPECTED',
    },
  },

  box: {
    id: 'e2e00001-0000-4000-8000-000000000005',
    trackingNumber: 'E2E-BOX-001',
  },

  products: {
    // (a) priced, GOOD, barcoded — the till/cart/reprint/catalog fixture.
    pricedGood: {
      id: 'e2e00001-0000-4000-8000-00000000000a',
      name: 'E2E Priced Kettle',
      category: 'KITCHEN',
      sellingPricePaise: 49900,
      mrpPaise: 59900,
      // The manufacturer barcode — what a Till/Unpacking scan reads off the pack.
      barcode: 'E2E0000000001',
      // The shelf barcode (BBZ) — what pricing mints and what Reprint/bulk-print resolve to.
      shelfBarcode: 'BBZ-100000',
      batchId: 'e2e00001-0000-4000-8000-00000000002a',
    },
    // (b) counted, GOOD, not yet priced — the pricing-workbench fixture.
    countedUnpriced: {
      id: 'e2e00001-0000-4000-8000-00000000000b',
      name: 'E2E Counted Toaster',
      category: 'KITCHEN',
      mrpPaise: 29900,
      barcode: 'E2E0000000002',
      batchId: 'e2e00001-0000-4000-8000-00000000002b',
    },
    // (c) NEEDS_WORK — the Prep backlog fixture.
    needsWork: {
      id: 'e2e00001-0000-4000-8000-00000000000c',
      name: 'E2E Needs Work Blender',
      category: 'KITCHEN',
      issueType: 'CLEAN',
      issueLabel: 'Clean',
      quantity: 3,
    },
    // (d) DAMAGED — the Prep "review damaged" fixture.
    damaged: {
      id: 'e2e00001-0000-4000-8000-00000000000d',
      name: 'E2E Damaged Mixer',
      category: 'ELECTRONICS',
      quantity: 2,
      remark: 'Dented body, scanned at unpacking',
    },
    // (e) still only on the manifest — the Unpacking-screen fixture.
    unpackingOnly: {
      id: 'e2e00001-0000-4000-8000-00000000000e',
      name: 'E2E Unpacking Widget',
      category: 'KITCHEN',
      expectedLineId: 'e2e00001-0000-4000-8000-000000000041',
      code: 'E2E-UNPACK-001',
      quantityExpected: 5,
    },
  },

  cart: {
    id: 'e2e00001-0000-4000-8000-000000000051',
  },

  // A PAID cart + SALE ledger entry — fixture data only, no screen reads it yet.
  completedSale: {
    cartId: 'e2e00001-0000-4000-8000-000000000053',
  },

  productCapture: {
    id: 'e2e00001-0000-4000-8000-000000000061',
    name: 'E2E Captured Sample',
  },

  printJob: {
    id: 'e2e00001-0000-4000-8000-000000000071',
    barcode: 'E2E0000000001',
    productName: 'E2E Priced Kettle',
    status: 'review',
  },

  printerConfig: {
    id: '00000000-0000-0000-0000-000000000001', // PrinterConfig.SINGLETON_ID
    address: '203.0.113.1:9100',
    enabled: false,
  },
} as const
