import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Item detail: KPIs, movement log, price history, and batches render for a seeded product; a bin edit persists across reload; a reprice writes a new price-history row', async ({ page }) => {
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name }).click()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()

  // --- KPI cells: cost basis, price, margin, sold/received (batch a: cost 20000, price 49900,
  // 10 received, 1 sold — fixed seed figures untouched by any earlier spec). ---
  const kpi = (label: string) => page.locator('.id-kpi', { hasText: label }).locator('.id-kpi-value')
  await expect(kpi('Cost basis')).toHaveText('₹200')
  await expect(kpi('Price')).toHaveText('₹499')
  await expect(kpi('Margin')).toHaveText('60%')
  await expect(kpi('Sold / Received')).toHaveText('1/10')

  // --- Movement log: the seeded receipt and sale, straight from the stock ledger. ---
  const movementRows = page.locator('.id-table').first().locator('tbody tr')
  await expect(movementRows.filter({ hasText: 'PURCHASE_RECEIPT' })).toBeVisible()
  await expect(movementRows.filter({ hasText: 'PURCHASE_RECEIPT' }).locator('td').nth(1)).toHaveText('+10')
  await expect(movementRows.filter({ hasText: 'SALE' })).toBeVisible()

  // --- Price history: the seeded row, old→new, first-set marker on the null old price. ---
  const priceHistoryTable = page.locator('h3', { hasText: 'Price history' }).locator('xpath=following-sibling::table[1]')
  let priceRows = priceHistoryTable.locator('tbody tr')
  await expect(priceRows).toHaveCount(1)
  await expect(priceRows.first().locator('td').nth(0)).toHaveText('First price')
  await expect(priceRows.first().locator('td').nth(1)).toHaveText('₹499')
  await expect(priceRows.first().locator('td').nth(2)).toHaveText(seed.priceHistory.kettle.operatorName)

  // --- Batch bin edit round-trips across a reload. ---
  const binInput = page.locator('.id-bin-input').first()
  await expect(binInput).toHaveValue(seed.products.pricedGood.bin)
  await binInput.fill('B-77');
  await page.locator('.id-bin-cell').first().getByRole('button', { name: 'Save' }).click()
  await expect(page.locator('.id-bin-cell').first().getByRole('button', { name: 'Save' })).toBeEnabled()

  await page.reload()
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name }).click()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
  await expect(page.locator('.id-bin-input').first()).toHaveValue('B-77')

  // --- Reprice through the actions rail writes a NEW price-history row — the journal proven
  // through the same stack a person actually uses (PUT /api/products/{id}/price, the shelf-pricing
  // choke point), not asserted only at the backend. ---
  await page.locator('.price-in').fill('550')
  await page.getByRole('button', { name: 'Save price' }).click()
  await expect(page.getByText('Price saved.')).toBeVisible()

  priceRows = priceHistoryTable.locator('tbody tr')
  await expect(priceRows).toHaveCount(2)
  await expect(priceRows.first().locator('td').nth(0)).toHaveText('₹499')
  await expect(priceRows.first().locator('td').nth(1)).toHaveText('₹550')
  await expect(kpi('Price')).toHaveText('₹550')

  // --- Queueing a label reprint from the actions rail (safe under test: printer_config.enabled
  // = 0, same as 07-reprint.spec.ts). ---
  await page.getByRole('button', { name: 'Queue label reprint' }).click()
  await expect(page.getByText('Label reprint queued.')).toBeVisible()

  // --- Category edit (palletworks-nav): PATCH /api/products/{id}/category persists a new
  // department, and touches neither price nor price history — the reprice above is the only
  // price-history row still on record, and the KPI price is unchanged. ---
  await page.getByLabel('Department').selectOption('HOME_ESSENTIALS')
  await page.getByRole('button', { name: 'Save department' }).click()
  await expect(page.getByText('Department saved.')).toBeVisible()
  await expect(priceHistoryTable.locator('tbody tr')).toHaveCount(2)
  await expect(kpi('Price')).toHaveText('₹550')

  await page.reload()
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name }).click()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
  await expect(page.getByLabel('Department')).toHaveValue('HOME_ESSENTIALS')
})

test('Item detail: Count for a product no delivery owes degrades to an honest empty grid', async ({ page }) => {
  // The damaged mixer has stock but no expected_line in any open delivery — picking the open
  // lot must state that nothing is outstanding, never crash or fabricate rows.
  await openScreen(page, 'Inventory')
  await page.locator('tr.inv-row', { hasText: seed.products.damaged.name }).click()
  await page.getByLabel('Delivery').selectOption({ index: 1 })
  await page.getByRole('button', { name: 'Count', exact: true }).click()
  await expect(page.getByText('Nothing outstanding for this product in this delivery.')).toBeVisible()
})
