import type { Page } from '@playwright/test'

/**
 * Loads the dashboard shell and clicks the named sidebar entry — the sidebar button's accessible
 * name is the same string as the screen's own header title (see Sidebar.tsx's screenMeta), so
 * every screen spec navigates the same way a person would: land on the shell, then pick a screen.
 *
 * Not for Capture — that screen is phone-only and reached by URL hash on a narrow viewport, never
 * through the sidebar (see capture.spec.ts).
 */
export async function openScreen(page: Page, label: string) {
  await page.goto('/')
  await page.getByRole('button', { name: label, exact: true }).click()
}
