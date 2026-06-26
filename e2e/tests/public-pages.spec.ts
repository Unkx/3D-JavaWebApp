import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';
import { join } from 'path';

test.describe('Public pages smoke', () => {
  test('home page loads with hero heading', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1#hero-title')).toContainText('Zlecaj druk 3D');
  });

  test('listings page loads with page title', async ({ page }) => {
    await page.goto('/zlecenia');
    await expect(page.locator('h1.page__title')).toContainText('Zlecenia druku');
  });

  test('login page renders login form', async ({ page }) => {
    await page.goto('/logowanie');
    await expect(page.locator('#login-email')).toBeVisible();
    await expect(page.locator('#login-pw')).toBeVisible();
    await expect(page.locator('button[type=submit]')).toBeVisible();
  });

  test('listing detail page loads the seeded listing', async ({ page }) => {
    const { id } = JSON.parse(
      readFileSync(join(__dirname, '../.auth/test-listing.json'), 'utf-8')
    ) as { id: string };

    await page.goto(`/zlecenia/${id}`);
    await expect(page.locator('h1.listing__title')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('h1.listing__title')).toContainText('E2E Smoke Test Listing');
  });
});
