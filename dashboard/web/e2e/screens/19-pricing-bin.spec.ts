import { test, expect } from '@playwright/test'
import { openScreen } from '../helpers'

// PricingWorkbench.tsx's PriceForm labels its fields with plain <label> elements that are not
// programmatically associated with their inputs (no htmlFor/id) — see the component. getByLabel
// cannot find them, so these locators walk from the label text to its next sibling control,
// exactly the structure Field() renders.
function fieldControl(page: import('@playwright/test').Page, label: string, tag: 'input' | 'select') {
  return page.locator(`label:text-is("${label}")`).locator(`xpath=following-sibling::${tag}[1]`)
}

test('Pricing: a hand-keyed product saved with a bin lands on its batch, and Inventory reflects it', async ({ page }) => {
  const productName = 'E2E Manual Bin Product'
  const binValue = '19-BIN-01'

  await openScreen(page, 'Pricing')

  // Pick the seeded (only) lot, then switch to the hand-add form.
  await fieldControl(page, 'Lot', 'select').selectOption({ index: 1 })
  await page.getByRole('button', { name: '+ Add by hand (not counted / no code)' }).click()

  await fieldControl(page, 'Name', 'input').fill(productName)
  await fieldControl(page, 'Category', 'select').selectOption('KITCHEN')
  await fieldControl(page, 'Bin (optional)', 'input').fill(binValue)
  await fieldControl(page, 'Selling price', 'input').fill('150')

  await page.getByRole('button', { name: 'Save product', exact: true }).click()
  await expect(page.getByText(new RegExp(`${productName} saved`))).toBeVisible()

  // Inventory reflects the same batch's bin — the pricing save and the Inventory read agree.
  await openScreen(page, 'Inventory')
  await page.getByPlaceholder('Search by product name').fill(productName)

  const row = page.locator('tr.inv-row', { hasText: productName })
  await expect(row).toBeVisible()
  await expect(row.locator('td').nth(3)).toHaveText(binValue)
})
