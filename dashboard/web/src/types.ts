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
