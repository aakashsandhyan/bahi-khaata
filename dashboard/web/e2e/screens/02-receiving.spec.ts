import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Receiving: opening the seeded lot lists its boxes, and receiving one updates its state', async ({ page }) => {
  await openScreen(page, 'Receiving')

  await page.getByText(seed.supplier.name).click()
  await expect(page.getByText(seed.boxReceipt.received.manifestCartonId)).toBeVisible()
  await expect(page.getByText(seed.boxReceipt.expected.manifestCartonId)).toBeVisible()
  await expect(page.getByText('RECEIVED', { exact: true })).toBeVisible()
  await expect(page.getByText('EXPECTED', { exact: true })).toBeVisible()

  await page.getByPlaceholder('Scan carton ID').fill(seed.boxReceipt.expected.manifestCartonId)
  await page.getByRole('button', { name: 'Receive', exact: true }).click()

  // The confirmation banner is deliberately transient (the lot reload right behind it clears the
  // message), so the durable, meaningful outcome to assert is the box's own row flipping state.
  const row = page.locator('.item', { hasText: seed.boxReceipt.expected.manifestCartonId })
  await expect(row.getByText('RECEIVED', { exact: true })).toBeVisible()
})
