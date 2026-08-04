import { test, expect } from '@playwright/test'
import { seed } from './seed'

// The harness's own self-check: before trusting any screen test, prove the backend answers, the
// shell renders against it, and the seed migration actually landed in the scratch database.

test('backend answers /api/instance', async ({ request }) => {
  const response = await request.get('/api/instance')
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body).toHaveProperty('sandbox')
})

test('the dashboard shell renders the sidebar', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('BACHAT BAZAAR')).toBeVisible()
})

test('the seed landed: the seeded lot is visible through the API', async ({ request }) => {
  const response = await request.get('/api/lots')
  expect(response.status()).toBe(200)
  const lots = await response.json()
  const seeded = lots.find((l: { id: string }) => l.id === seed.lot.id)
  expect(seeded, `seeded lot ${seed.lot.id} not found among ${JSON.stringify(lots)}`).toBeTruthy()
  expect(seeded.supplier).toBe(seed.supplier.name)
  expect(seeded.expected).toBe(2) // the two seeded box_receipt rows
})
