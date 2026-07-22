// The shapes the backend sends, mirrored from the contracts records so the compiler catches a
// drift the moment a field is added or renamed on either side. Paise throughout — money is an
// integer count of the smallest unit, never a float.

export interface PriceableItem {
  productId: string
  name: string
  categoryCode: string
  unitCostPaise: number
  onlinePricePaise: number | null
  mrpPaise: number | null
  mrpIsEstimate: boolean
  currentPricePaise: number | null
  condition: string
  remark: string | null
}

export interface PriceProposal {
  productId: string
  pricePaise: number
  grossMarginPercent: number
  percentOfMrp: number | null
  beatsOnline: boolean | null
  aboveMrp: boolean
}

export interface BulkResult {
  priced: number
  skippedAlreadyPriced: number
  skippedAboveMrp: number
}

// --- unpacking ---------------------------------------------------------------------------

export interface DeliveryProgress {
  lotId: string
  supplier: string
  category: string
  cartonsTotal: number
  cartonsFinished: number
  cartonsStarted: number
  unitsExpected: number
  unitsCounted: number
  unitsUnlisted: number
  itemsWithoutMrp: number
  closed: boolean
}

export interface UnpackingCarton {
  boxId: string
  trackingNumber: string
  lotId: string
  finished: boolean
}

export interface UnpackingLine {
  lineId: string
  code: string
  name: string
  expected: number
  counted: number
  outstanding: number
  needsMrp: boolean
  statedValuePaise: number | null
  onlinePricePaise: number | null
  indicativeCostPaise: number | null
}

export interface CountOutcome {
  batchId: string
  quantityExpected: number
  quantityCounted: number
  discrepancy: number
}

export interface SuggestedMrp {
  pricePaise: number | null
  source: string | null
  message: string | null
}

export interface LearntCode {
  code: string
  origin: string
  releasable: boolean
}
