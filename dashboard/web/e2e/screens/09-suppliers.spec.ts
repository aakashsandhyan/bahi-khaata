import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Suppliers: the seeded supplier is listed and its detail shows its lot', async ({ page }) => {
  await openScreen(page, 'Suppliers')

  await page.getByText(seed.supplier.name).click()

  await expect(page.getByRole('heading', { name: seed.supplier.name })).toBeVisible()
  await expect(page.getByText('Lots received')).toBeVisible()
  await expect(page.getByText(seed.lot.receivedOn)).toBeVisible()
})
