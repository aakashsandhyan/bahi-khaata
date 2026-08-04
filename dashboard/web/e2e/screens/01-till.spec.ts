import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Till: keying a seeded barcode adds a cart line', async ({ page }) => {
  await openScreen(page, 'Till')

  const scan = page.getByPlaceholder('Scan barcode or product code')
  await expect(scan).toBeVisible()
  await scan.fill(seed.products.pricedGood.barcode)
  await scan.press('Enter')

  await expect(page.getByText(seed.products.pricedGood.name)).toBeVisible()
  // 49900 paise — scoped to the line total, since the price also appears struck/unstruck
  // elsewhere on the line and in the subtotal row.
  await expect(page.locator('.till-line-total')).toHaveText('₹499')
  await expect(page.getByText('Subtotal')).toBeVisible()
})
