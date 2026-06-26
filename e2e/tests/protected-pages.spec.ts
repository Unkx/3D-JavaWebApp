import { test, expect } from '@playwright/test';
import { join } from 'path';

// Reuse the browser auth state saved by global-setup — no repeated login
test.use({ storageState: join(__dirname, '../.auth/user.json') });

test.describe('Protected pages smoke (logged-in)', () => {
  test('/profil renders profile heading', async ({ page }) => {
    await page.goto('/profil');
    await expect(page.getByRole('heading', { name: 'Mój profil', level: 1 })).toBeVisible();
  });

  test('/moje-zlecenia renders page heading', async ({ page }) => {
    await page.goto('/moje-zlecenia');
    await expect(page.getByRole('heading', { name: 'Moje zlecenia', level: 1 })).toBeVisible();
  });

  test('/wiadomosci renders page heading', async ({ page }) => {
    await page.goto('/wiadomosci');
    await expect(page.getByRole('heading', { name: 'Wiadomości', level: 1 })).toBeVisible();
  });
});
