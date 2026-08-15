import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Inventory: loads via sidebar, shows seeded rows with condition tags, a bin filter narrows the totals, a binless row shows an em dash, and a row opens item detail', async ({ page }) => {
  await openScreen(page, 'Inventory')

  // The seeded kettle (bin set, GOOD) and toaster (no bin) both render as rows.
  const kettleRow = page.locator('tr.inv-row', { hasText: seed.products.pricedGood.name })
  const toasterRow = page.locator('tr.inv-row', { hasText: seed.products.countedUnpriced.name })
  await expect(kettleRow).toBeVisible()
  await expect(toasterRow).toBeVisible()

  // Condition tag on the kettle's row.
  await expect(kettleRow.locator('.tag')).toHaveText('Good')

  // The kettle's seeded bin shows on its row.
  await expect(kettleRow.locator('td').nth(3)).toHaveText(seed.products.pricedGood.bin)
  // An unpriced product renders an em dash in the price column, never a zero amount. The damaged
  // mixer is the witness: unlike the counted toaster, nothing earlier in the suite prices it.
  const mixerRow = page.locator('tr.inv-row', { hasText: seed.products.damaged.name })
  await expect(mixerRow.locator('td').nth(6)).toHaveText('\u2014')

  // The toaster's batch was never given a bin — an em dash, not a blank cell.
  const toasterCells = toasterRow.locator('td')
  await expect(toasterCells.nth(3)).toHaveText('—')

  // The kettle's own on-hand figure and age, read off its row before filtering — the reference
  // values every filter assertion below checks against, without hard-coding a cross-spec figure
  // that could drift if another spec's run order changes what else is on the floor.
  const kettleOnHand = (await kettleRow.locator('td').nth(4).innerText()).trim()
  const kettleAgeDays = parseInt((await kettleRow.locator('td').nth(8).innerText()).trim(), 10)

  // --- A filter (bin) narrows the totals. ---
  await page.getByLabel('Bin').selectOption(seed.products.pricedGood.bin)
  await expect(kettleRow).toBeVisible()
  await expect(toasterRow).not.toBeVisible()
  const totals = page.locator('tr.inv-totals')
  await expect(totals.locator('td').nth(1)).toHaveText(kettleOnHand)
  await page.getByLabel('Bin').selectOption('')

  // --- Search matches product identity. ---
  await page.getByPlaceholder('Search by product name').fill('Priced Kettle')
  await expect(kettleRow).toBeVisible()
  await expect(toasterRow).not.toBeVisible()
  await page.getByPlaceholder('Search by product name').fill('')

  // --- Aging filter selects a bucket, computed from the kettle's own displayed age so this
  // holds whatever the real calendar date is when the suite runs. ---
  const bucketFor = (days: number) => (days <= 30 ? '0-30' : days <= 60 ? '31-60' : days <= 90 ? '61-90' : '90+')
  const matchingBucket = bucketFor(kettleAgeDays)
  const otherBucket = matchingBucket === '0-30' ? '90+' : '0-30'
  await page.getByLabel('Aging').selectOption(matchingBucket)
  await expect(kettleRow).toBeVisible()
  await page.getByLabel('Aging').selectOption(otherBucket)
  await expect(kettleRow).not.toBeVisible()
  await page.getByLabel('Aging').selectOption('')

  // --- Client-side CSV export reflects the currently filtered set, with no server round trip:
  // filter to the kettle alone, export, and the file holds it but not the toaster. ---
  await page.getByLabel('Bin').selectOption(seed.products.pricedGood.bin)
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Export CSV' }).click(),
  ])
  expect(download.suggestedFilename()).toBe('inventory.csv')
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream) chunks.push(chunk as Buffer)
  const csv = Buffer.concat(chunks).toString('utf-8')
  expect(csv).toContain(seed.products.pricedGood.name)
  expect(csv).not.toContain(seed.products.countedUnpriced.name)
  await page.getByLabel('Bin').selectOption('')

  // A row click opens Item detail for that product.
  await kettleRow.click()
  await expect(page.getByRole('heading', { name: seed.products.pricedGood.name })).toBeVisible()
})
