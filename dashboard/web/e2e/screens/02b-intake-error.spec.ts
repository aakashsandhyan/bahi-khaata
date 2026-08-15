import { test, expect } from '@playwright/test'
import { openScreen } from '../helpers'

// The stats aggregate failing must degrade the header to honest dashes, not take the screen —
// the failure is injected client-side, indistinguishable to the page from the endpoint dying.
test('Intake: a failing stats call degrades the header, and the tabs stay alive', async ({ page }) => {
  await page.route('**/api/lots/*/stats', (route) => route.abort())
  await openScreen(page, 'Intake')

  await expect(page.locator('.intake-rail-item').first()).toBeVisible()
  await expect(page.locator('.intake-kpi-value').first()).toHaveText('—')
  await page.getByRole('button', { name: 'Lines', exact: true }).click()
  await expect(page.locator('.inv-table')).toBeVisible()
})
