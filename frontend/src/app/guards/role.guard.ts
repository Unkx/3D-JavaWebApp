import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.createUrlTree(['/logowanie']);
  if (auth.isAdmin())     return true;
  return router.createUrlTree(['/profil']);
};

export const userOnlyGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) return router.createUrlTree(['/logowanie']);
  if (!auth.isAdmin())    return true;
  return router.createUrlTree(['/admin']);
};
