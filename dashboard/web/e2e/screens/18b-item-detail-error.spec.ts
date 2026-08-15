import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

// Mirrors 13b-dashboard-error.spec.ts's technique: the backend here is real and healthy, so the
// failure/delay is injected client-side by intercepting the one call Item detail makes.
test('Item detail: shows a loading indicator while the detail is in flight, and a plain error on fetch failure', async ({ page }) => {
  // --- Loading state: hold the response open briefly so the loading text is observable. ---
  await page.route('**/api/inventory/product/**', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500))
    await route.continue()
  })
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name }).click()
  await expect(page.getByText('Loading…')).toBeVisible()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
  await page.unroute('**/api/inventory/product/**')

  // --- Error state: the fetch fails outright, and the view shows a clear error, not a blank
  // or broken screen. ---
  await page.route('**/api/inventory/product/**', (route) => route.abort())
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name }).click()
  await expect(page.locator('.banner.stop')).toBeVisible()
})
