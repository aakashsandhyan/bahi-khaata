import { test, expect } from '@playwright/test'

// The sidebar becomes an overlay drawer at phone widths (≤760px): hidden until the hamburger
// opens it, closed again by the scrim. Desktop navigation itself is exercised by every screen
// spec through openScreen(); this covers the phone-only shell behavior.
test.use({ viewport: { width: 420, height: 800 } })

test('Shell: at phone width the sidebar is a drawer that opens and closes', async ({ page }) => {
  await page.goto('/')

  const sidebar = page.locator('.sidebar')
  await expect(sidebar).not.toBeInViewport()

  await page.getByRole('button', { name: 'Open navigation' }).click()
  await expect(sidebar).toBeInViewport()
  await expect(page.getByRole('button', { name: 'Intake', exact: true })).toBeVisible()

  await page.locator('.sidebar-scrim').click()
  await expect(sidebar).not.toBeInViewport()
})
