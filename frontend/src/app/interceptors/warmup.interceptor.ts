import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { BackendWarmupService } from '../services/backend-warmup.service';

export const warmupInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/')) {
    return next(req);
  }
  const warmup = inject(BackendWarmupService);
  warmup.requestStarted();
  return next(req).pipe(finalize(() => warmup.requestEnded()));
};
