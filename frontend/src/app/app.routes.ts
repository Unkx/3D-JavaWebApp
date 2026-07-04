import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard, userOnlyGuard } from './guards/role.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent) },
  { path: 'zlecenia', loadComponent: () => import('./features/listings/listings.component').then(m => m.ListingsComponent) },
  { path: 'faq', loadComponent: () => import('./features/faq/faq.component').then(m => m.FaqComponent) },
  { path: 'polityka-prywatnosci', loadComponent: () => import('./features/privacy-policy/privacy-policy.component').then(m => m.PrivacyPolicyComponent) },
  { path: 'regulamin', loadComponent: () => import('./features/terms-of-use/terms-of-use.component').then(m => m.TermsOfUseComponent) },
  { path: 'zlecenia/:id', loadComponent: () => import('./features/listing-detail/listing-detail.component').then(m => m.ListingDetailComponent) },
  {
    path: 'dodaj-zlecenie',
    canActivate: [userOnlyGuard],
    loadComponent: () => import('./features/add-listing/add-listing.component').then(m => m.AddListingComponent)
  },
  { path: 'logowanie', loadComponent: () => import('./features/auth/auth.component').then(m => m.AuthComponent) },
  {
    path: 'profil',
    canActivate: [userOnlyGuard],
    loadComponent: () => import('./features/user-panel/user-panel.component').then(m => m.UserPanelComponent)
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin-panel/admin-panel.component').then(m => m.AdminPanelComponent)
  },
  {
    path: 'moje-zlecenia',
    canActivate: [userOnlyGuard],
    loadComponent: () => import('./features/my-orders/my-orders.component').then(m => m.MyOrdersComponent)
  },
  {
    path: 'wiadomosci',
    canActivate: [userOnlyGuard],
    loadComponent: () => import('./features/messages/messages.component').then(m => m.MessagesComponent)
  },
  { path: 'reset-password', loadComponent: () => import('./features/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
  {
    path: '**',
    data: { fullscreen: true },
    loadComponent: () => import('./features/not-found/not-found.component').then(m => m.NotFoundComponent)
  }
];
