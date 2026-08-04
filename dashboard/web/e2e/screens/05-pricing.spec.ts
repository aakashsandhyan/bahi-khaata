import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Pricing: pricing the seeded counted product saves it with a barcode', async ({ page }) => {
  await openScreen(page, 'Pricing')

  // The "direct scan" box (no lot chosen) resolves any already-counted product by its code — used
  // here rather than the per-lot scan box so there is only one "Scan LSN / ASIN…" input on screen.
  const scan = page.getByPlaceholder('Scan LSN / ASIN…')
  await scan.fill(seed.products.countedUnpriced.barcode)
  await scan.press('Enter')

  await expect(page.getByRole('heading', { name: 'Price this item' })).toBeVisible()

  await page.getByPlaceholder('₹', { exact: true }).fill('199')
  await page.getByRole('button', { name: 'Save product', exact: true }).click()

  await expect(page.getByText(new RegExp(`${seed.products.countedUnpriced.name} saved`))).toBeVisible()
})
