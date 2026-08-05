import { test, expect } from '@playwright/test'

// The dashboard is the desktop landing, so a failing aggregate greets every open — the spec
// requires it to degrade to a plain error without wedging navigation. The backend here is the
// real one and healthy, so the failure is injected client-side: aborting the aggregate call is
// indistinguishable, to the page, from the backend being down.
test('Dashboard: a failing aggregate shows a plain error and navigation stays usable', async ({ page }) => {
  await page.route('**/api/dashboard', (route) => route.abort())
  await page.goto('/')

  await expect(page.locator('.banner.stop')).toBeVisible()

  // Till is unlisted (design decision D8 of palletworks-nav) — no longer a sidebar entry to prove
  // navigation with, so this reaches for another one instead. Which screen is incidental; the
  // point is that the dashboard's own failure never wedges the sidebar.
  await page.getByRole('button', { name: 'Inventory', exact: true }).click()
  await expect(page.getByPlaceholder('Search by product name')).toBeVisible()
})
