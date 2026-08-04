import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

test('Prep: the seeded needs-work product is in the backlog and opens its states', async ({ page }) => {
  await openScreen(page, 'Prep')

  await expect(page.getByText(seed.products.needsWork.name)).toBeVisible()
  await page.getByRole('button', { name: new RegExp(seed.products.needsWork.name) }).click()

  await expect(page.getByRole('heading', { name: seed.products.needsWork.name })).toBeVisible()
  await expect(page.getByText(`Needs work · ${seed.products.needsWork.issueLabel}`)).toBeVisible()
  await expect(page.getByText(`${seed.products.needsWork.quantity} units — tap to move`)).toBeVisible()
})
