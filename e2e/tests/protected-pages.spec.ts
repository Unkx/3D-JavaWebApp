import { test, expect } from '@playwright/test';
import { join } from 'path';

// Reuse the browser auth state saved by global-setup — no repeated login
test.use({ storageState: join(__dirname, '../.auth/user.json') });

test.describe('Protected pages smoke (logged-in)', () => {
  test('/profil renders profile heading', async ({ page }) => {
    await page.goto('/profil');
    await expect(page.locator('h1.page__title')).toContainText('Mój profil');
  });

  test('/moje-zlecenia renders page heading', async ({ page }) => {
    await page.goto('/moje-zlecenia');
    await expect(page.locator('h1.page__title')).toContainText('Moje zlecenia');
  });

  test('/wiadomosci renders page heading', async ({ page }) => {
    await page.goto('/wiadomosci');
    await expect(page.locator('h1.page__title')).toContainText('Wiadomości');
  });
});
