import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent) },
  { path: 'zlecenia', loadComponent: () => import('./features/listings/listings.component').then(m => m.ListingsComponent) },
  { path: 'zlecenia/:id', loadComponent: () => import('./features/listing-detail/listing-detail.component').then(m => m.ListingDetailComponent) },
  {
    path: 'dodaj-zlecenie',
    canActivate: [authGuard],
    loadComponent: () => import('./features/add-listing/add-listing.component').then(m => m.AddListingComponent)
  },
  { path: 'logowanie', loadComponent: () => import('./features/auth/auth.component').then(m => m.AuthComponent) },
  {
    path: 'profil',
    canActivate: [authGuard],
    loadComponent: () => import('./features/user-panel/user-panel.component').then(m => m.UserPanelComponent)
  },
  {
    path: 'moje-zlecenia',
    canActivate: [authGuard],
    loadComponent: () => import('./features/my-orders/my-orders.component').then(m => m.MyOrdersComponent)
  },
  {
    path: 'wiadomosci',
    canActivate: [authGuard],
    loadComponent: () => import('./features/messages/messages.component').then(m => m.MessagesComponent)
  },
  { path: 'reset-password', loadComponent: () => import('./features/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
  { path: '**', redirectTo: '' }
];
