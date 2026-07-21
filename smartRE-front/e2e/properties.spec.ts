import { test, expect } from '@playwright/test'

test.describe('Property listings', () => {
  test('loads without login and shows the filter bar', async ({ page }) => {
    await page.goto('/properties')
    await expect(page).not.toHaveURL(/login/)
    await expect(page.getByPlaceholder(/search title, estate/i)).toBeVisible()
  })

  test('filters panel toggles open', async ({ page }) => {
    await page.goto('/properties')
    await page.getByRole('button', { name: /filters/i }).click()
    await expect(page.getByPlaceholder('County')).toBeVisible()
  })

  test('county filter updates the URL', async ({ page }) => {
    await page.goto('/properties')
    await page.getByRole('button', { name: /filters/i }).click()
    await page.getByPlaceholder('County').fill('Nairobi')
    await expect(page).toHaveURL(/county=Nairobi/, { timeout: 5000 })
  })

  test('empty or populated state renders without a crash', async ({ page }) => {
    await page.goto('/properties')
    const hasCards = await page.locator('a[href^="/properties/"]').count()
    const hasEmptyState = await page.getByText(/no properties match/i).count()
    expect(hasCards > 0 || hasEmptyState > 0).toBeTruthy()
  })
})
