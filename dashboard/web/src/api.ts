// The only route to the backend. Every call goes through here, so the base address, the error
// handling, and the fact that this is a client of a separate service all live in one place.
//
// A refusal the backend wrote — a price above the MRP, a cost not yet known — comes back as a
// sentence meant for a person. It is thrown as-is rather than turned into "request failed",
// because the sentence is the useful part.

import type { BulkResult, PriceProposal, PriceableItem } from './types'

// Relative, so every call goes through this server, which proxies it to the backend. That keeps
// the browser on one secure origin — needed for the camera — with no mixed content and no
// cross-origin request. An explicit VITE_BACKEND still overrides it for anyone pointing the app
// at a backend elsewhere.
const BASE = import.meta.env.VITE_BACKEND ?? ''

class BackendError extends Error {}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`)
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T>
}

// For endpoints that answer with a list, where a 404 means "nothing here" rather than a fault —
// an unknown box tracking number, a code that resolves to nothing. Returns an empty list so the
// caller shows its ordinary "not found" message instead of a raw backend error.
async function getList<T>(path: string): Promise<T[]> {
  const response = await fetch(`${BASE}${path}`)
  if (response.status === 404) return []
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T[]>
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
  LearntCode as _LearntCode,
  SuggestedMrp as _SuggestedMrp,
  UnpackingCarton as _UnpackingCarton,
  UnpackingLine as _UnpackingLine,
} from './types'

function countBody(
  quantity: number,
  mrpPaise: number | null,
  condition: string,
  remark: string | null,
) {
  const body: Record<string, unknown> = { quantity, mrpIsEstimate: false, condition }
  if (mrpPaise != null) body.mrpPaise = mrpPaise
  if (remark) body.remark = remark
  return body
}

export const unpacking = {
  deliveries: () => get<_DeliveryProgress[]>('/api/unpacking/deliveries'),

  cartonsByTracking: (tracking: string) =>
    getList<_UnpackingCarton>(`/api/unpacking/boxes/by-tracking/${encodeURIComponent(tracking)}`),

  lines: (boxId: string) => getList<_UnpackingLine>(`/api/unpacking/boxes/${boxId}/lines`),

  resolve: (boxId: string, code: string) =>
    getList<_UnpackingLine>(`/api/unpacking/boxes/${boxId}/resolve?code=${encodeURIComponent(code)}`),

  count: (
    lineId: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
  ) =>
    post<_CountOutcome>(
      `/api/unpacking/lines/${lineId}/count`,
      countBody(quantity, mrpPaise, condition, remark),
    ),

  tag: (
    lineId: string,
    scannedCode: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
  ) =>
    post<_CountOutcome>(`/api/unpacking/lines/${lineId}/tag`, {
      scannedCode,
      ...countBody(quantity, mrpPaise, condition, remark),
    }),

  suggestedMrp: (lineId: string) =>
    get<_SuggestedMrp>(`/api/unpacking/lines/${lineId}/suggested-mrp`),

  finishCarton: (boxId: string) => post<void>(`/api/unpacking/boxes/${boxId}/finish`),

  // --- corrections ---
  // A count taken back is a new reversing entry, never a deleted one — the ledger is
  // append-only. A condition left null means "whichever it was counted as".
  undo: (lineId: string, quantity: number) =>
    post<void>(`/api/unpacking/lines/${lineId}/undo`, { quantity }),

  // Every code that scans as a line's goods, and which may be given up.
  codesFor: (lineId: string) => get<_LearntCode[]>(`/api/unpacking/lines/${lineId}/codes`),

  // Forget a code put on the wrong goods, so the sticker can be scanned onto the right item.
  releaseCode: (code: string) =>
    post<void>(`/api/unpacking/codes/${encodeURIComponent(code)}/release`),
}
