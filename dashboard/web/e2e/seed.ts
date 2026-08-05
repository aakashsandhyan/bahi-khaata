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
      // palletworks-inventory: this batch carries a real bin, for the Inventory/Item-detail bin
      // display and filter smokes.
      bin: 'A-01',
    },
    // (b) counted, GOOD, not yet priced — the pricing-workbench fixture.
    countedUnpriced: {
      id: 'e2e00001-0000-4000-8000-00000000000b',
      name: 'E2E Counted Toaster',
      category: 'KITCHEN',
      mrpPaise: 29900,
      barcode: 'E2E0000000002',
      batchId: 'e2e00001-0000-4000-8000-00000000002b',
      // palletworks-inventory: left unset on purpose, for the em-dash-on-no-bin smoke.
      bin: null as string | null,
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

  receiptPrinterConfig: {
    id: '00000000-0000-0000-0000-000000000002',
    address: '203.0.113.1:9101',
    enabled: false,
    // V44 (an earlier, unrelated migration) leaves this config's transport as USB, not V43's LAN
    // default — the seed only re-patches address/enabled back to a safe TEST-NET state, per the
    // comment on that UPDATE in R__e2e_seed.sql. 15-receipt-config.spec.ts locates the address
    // field by role rather than by its (transport-dependent) placeholder to avoid coupling to this.
    transport: 'USB',
  },

  // Two completed bills on the priced kettle (products.pricedGood), timestamped `now` by the seed
  // migration itself (see R__e2e_seed.sql) so they always land in today's IST window. Figures are
  // computed by hand from pricedGood's seeded price (49900) and MRP (59900).
  sales: {
    first: {
      id: 'e2e00001-0000-4000-8000-000000000081',
      billNo: 9001,
      billNoFormatted: 'BB-009001',
      paymentMethod: 'CASH',
      quantity: 2,
      totalPaise: 99800,
    },
    second: {
      id: 'e2e00001-0000-4000-8000-000000000082',
      billNo: 9002,
      billNoFormatted: 'BB-009002',
      paymentMethod: 'UPI',
      quantity: 1,
      totalPaise: 49900,
    },
    // Sum of both bills — the exact figure the dashboard's revenue-today tile must show. Non-zero
    // on purpose: this is the tripwire for the seed's timestamp format (see D10).
    revenueTodayPaise: 99800 + 49900,
    operatorName: 'E2E Seed',
  },

  // One fixed price-change row for the priced kettle (products.pricedGood) — palletworks-inventory:
  // the item-detail price-history section and the price-change journal read against this. old is
  // null (the product's first-ever price set, matching how it was actually seeded).
  priceHistory: {
    kettle: {
      id: 'e2e00001-0000-4000-8000-0000000000a1',
      oldPricePaise: null as number | null,
      newPricePaise: 49900,
      operatorName: 'E2E Seed',
    },
  },

  // V43 defaults are what the Bill settings screen should show — except shop_name, which V44 (an
  // earlier, unrelated migration) overwrites to the shop's Devanagari name on every environment,
  // including this scratch database. That is real, current production behaviour, not something
  // this seed re-patches; 16-bill-settings.spec.ts asserts the actual value rather than the stale
  // V43 default. See the implementation return for palletworks-dashboard for the full note.
  billSettings: {
    shopNameAsSeeded: 'बचत बाज़ार', // V44's overwrite, not the V43 default 'Bachat Bazaar'
    billTitle: 'Bill of Supply',
    footer: 'Thank you!',
  },
} as const
