// The only route to the backend. Every call goes through here, so the base address, the error
// handling, and the fact that this is a client of a separate service all live in one place.
//
// A refusal the backend wrote — a price above the MRP, a cost not yet known — comes back as a
// sentence meant for a person. It is thrown as-is rather than turned into "request failed",
// because the sentence is the useful part.

import type { BulkResult, PriceProposal, PriceableItem } from './types'

const BASE = import.meta.env.VITE_BACKEND ?? 'http://localhost:8080'

class BackendError extends Error {}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`)
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T>
}

async function post<T>(path: string, body?: unknown): Promise<T | null> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) throw new BackendError(await message(response))
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : null
}

async function message(response: Response): Promise<string> {
  const text = await response.text()
  return text || `The backend answered ${response.status}.`
}

export const api = {
  priceable: (category: string) =>
    get<PriceableItem[]>(`/api/admin/pricing/priceable?category=${encodeURIComponent(category)}`),

  preview: (category: string, marginPercent: number) =>
    get<PriceProposal[]>(
      `/api/admin/pricing/preview?category=${encodeURIComponent(category)}&marginPercent=${marginPercent}`,
    ),

  setPrice: (productId: string, pricePaise: number) =>
    post<void>(`/api/admin/pricing/products/${productId}`, { pricePaise }),

  priceCategory: (category: string, marginPercent: number) =>
    post<BulkResult>(
      `/api/admin/pricing/category/${encodeURIComponent(category)}?marginPercent=${marginPercent}`,
    ),
}

export { BackendError }

// --- unpacking -----------------------------------------------------------------------------
// The same operations the terminal drives, exposed to the web so several people can scan at
// once. A condition of "GOOD" is sent explicitly; a null MRP is left out of the body entirely,
// because an empty string is not a valid figure and the backend would refuse the whole request.

import type {
  CountOutcome as _CountOutcome,
  DeliveryProgress as _DeliveryProgress,
  SuggestedMrp as _SuggestedMrp,
  UnpackingCarton as _UnpackingCarton,
  UnpackingLine as _UnpackingLine,
} from './types'

function countBody(quantity: number, mrpPaise: number | null, condition: string) {
  const body: Record<string, unknown> = { quantity, mrpIsEstimate: false, condition }
  if (mrpPaise != null) body.mrpPaise = mrpPaise
  return body
}

export const unpacking = {
  deliveries: () => get<_DeliveryProgress[]>('/api/unpacking/deliveries'),

  cartonsByTracking: (tracking: string) =>
    get<_UnpackingCarton[]>(`/api/unpacking/boxes/by-tracking/${encodeURIComponent(tracking)}`),

  lines: (boxId: string) => get<_UnpackingLine[]>(`/api/unpacking/boxes/${boxId}/lines`),

  resolve: (boxId: string, code: string) =>
    get<_UnpackingLine[]>(`/api/unpacking/boxes/${boxId}/resolve?code=${encodeURIComponent(code)}`),

  count: (lineId: string, quantity: number, mrpPaise: number | null, condition: string) =>
    post<_CountOutcome>(`/api/unpacking/lines/${lineId}/count`, countBody(quantity, mrpPaise, condition)),

  tag: (lineId: string, scannedCode: string, quantity: number, mrpPaise: number | null, condition: string) =>
    post<_CountOutcome>(`/api/unpacking/lines/${lineId}/tag`, {
      scannedCode,
      ...countBody(quantity, mrpPaise, condition),
    }),

  suggestedMrp: (lineId: string) =>
    get<_SuggestedMrp>(`/api/unpacking/lines/${lineId}/suggested-mrp`),

  finishCarton: (boxId: string) => post<void>(`/api/unpacking/boxes/${boxId}/finish`),
}
