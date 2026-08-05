import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Catalog: the seeded priced product is found by name and its detail opens', async ({ page }) => {
  await openScreen(page, 'Catalog')

  await page.getByPlaceholder('Find a product by name').fill('E2E Priced Kettle')
  await page.getByRole('button', { name: 'Found', exact: true }).click()

  await expect(page.getByText(seed.products.pricedGood.name)).toBeVisible()
  await page.getByText(seed.products.pricedGood.name).click()

  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
  await expect(page.getByText(seed.products.pricedGood.barcode)).toBeVisible()

  // palletworks-inventory D9: the panel's own link opens Item detail for the same product, via
  // App's onOpenItem — the same mechanism an Inventory row uses.
  await page.getByRole('button', { name: 'Open in Item detail →' }).click()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
  await expect(page.locator('.id-kpis')).toBeVisible()
})
