import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Sales: both seeded bills are listed with their totals', async ({ page }) => {
  // The modern sidebar labels the bill record "Invoices" (palletworks-selling); same screen.
  await openScreen(page, 'Invoices')

  const firstRow = page.locator('tr', { hasText: seed.sales.first.billNoFormatted })
  await expect(firstRow).toContainText('₹998') // 99,800 paise

  const secondRow = page.locator('tr', { hasText: seed.sales.second.billNoFormatted })
  await expect(secondRow).toContainText('₹499') // 49,900 paise
})

// The bill opens as a modal from its row: lines, totals, walk-in labeling, reprint at hand
// (selling-screens spec: an invoice opens as a details modal).
test('A bill row opens the details modal with its lines and Walk-in label', async ({ page }) => {
  await openScreen(page, 'Invoices')

  await page.locator('tr', { hasText: seed.sales.first.billNoFormatted }).click()
  const modal = page.getByRole('dialog')
  await expect(modal.getByRole('heading', { name: seed.sales.first.billNoFormatted })).toBeVisible()
  await expect(modal.getByText('Walk-in')).toBeVisible() // seeded bills predate capture
  await expect(modal.locator('.bill-grand')).toContainText('₹998')
  await expect(modal.getByRole('button', { name: 'Reprint bill', exact: true })).toBeVisible()
  await modal.getByRole('button', { name: 'Done', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
