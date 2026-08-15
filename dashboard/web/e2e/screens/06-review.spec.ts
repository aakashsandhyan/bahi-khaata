import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Review: the seeded capture and label entry are listed, and approving the capture prices it', async ({ page }) => {
  await openScreen(page, 'Review')

  // The seeded print_job (status 'review') shows up in "Labels to print".
  await expect(page.getByText(seed.printJob.productName).first()).toBeVisible()

  // The seeded product_capture (status 'pending') shows up in "Captures to finish".
  await expect(page.getByText(seed.productCapture.name)).toBeVisible()

  // Scoped to <main>: the sidebar's own "Review" nav button has the same accessible name.
  await page.getByRole('main').getByRole('button', { name: 'Review', exact: true }).click()
  const selects = page.locator('select')
  await selects.nth(0).selectOption(seed.lot.id) // Lot

  // The category select only fills in after the lot pick triggers a fetch of that lot's
  // categories — wait for the option to attach rather than race it.
  const category = selects.nth(1)
  await expect(category.locator(`option[value="${seed.products.needsWork.category}"]`)).toBeAttached()
  await category.selectOption(seed.products.needsWork.category) // 'KITCHEN'
  await page.getByPlaceholder('Selling price').fill('149')
  await page.getByRole('button', { name: 'Approve → shelf' }).click()

  await expect(page.getByText(seed.productCapture.name)).not.toBeVisible()
})
