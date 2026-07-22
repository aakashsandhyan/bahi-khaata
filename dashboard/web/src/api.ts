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
