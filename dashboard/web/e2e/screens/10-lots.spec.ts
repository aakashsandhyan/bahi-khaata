import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Lots: the seeded lot is listed, and a new manual lot can be created for the seeded supplier', async ({ page }) => {
  await openScreen(page, 'Lots')

  await expect(page.getByText(seed.supplier.name).first()).toBeVisible()

  await page.getByRole('button', { name: '+ Create Lot' }).click()
  const modal = page.locator('.modal-content')
  await modal.locator('select').nth(0).selectOption({ label: seed.supplier.name })
  await modal.locator('input[type="date"]').fill('2026-08-02')
  await modal.locator('input[type="number"]').fill('1000')
  await modal.locator('select').nth(1).selectOption('manual') // manual, not manifest-import
  await modal.getByRole('button', { name: 'Create', exact: true }).click()

  await expect(page.getByText(`✓ ${seed.supplier.name} created`)).toBeVisible()

  // The manual lot just created has no manifest boxes, so nothing can ever complete its
  // receiving automatically — the hand action is the only path, and it moves the lot from
  // In Progress to Complete (which is also what clears the dashboard's still-receiving alert).
  const freshLot = page.locator('.ov', { hasText: '2026-08-02' }).first()
  await freshLot.getByRole('button', { name: 'Receiving finished' }).click()
  await expect(freshLot).toBeHidden()
  await page.getByRole('button', { name: /Complete \(/ }).click()
  await expect(page.locator('.ov', { hasText: '2026-08-02' }).first()).toBeVisible()
})
