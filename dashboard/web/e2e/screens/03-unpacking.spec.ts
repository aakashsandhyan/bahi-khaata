import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Unpacking: scanning the seeded box lists its expected item', async ({ page }) => {
  await openScreen(page, 'Unpacking')

  const scan = page.getByPlaceholder('Scan here')
  await scan.fill(seed.box.trackingNumber)
  await scan.press('Enter')

  // Scoped to the heading — once the box is open it is also echoed in the "recent boxes" rail,
  // so a plain text lookup for the tracking number would match twice.
  await expect(page.getByRole('heading', { name: new RegExp(seed.box.trackingNumber) })).toBeVisible()
  await expect(page.getByText(seed.products.unpackingOnly.name)).toBeVisible()
  await expect(page.getByText(`0 / ${seed.products.unpackingOnly.quantityExpected}`)).toBeVisible()
})
