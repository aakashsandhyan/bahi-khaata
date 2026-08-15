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
