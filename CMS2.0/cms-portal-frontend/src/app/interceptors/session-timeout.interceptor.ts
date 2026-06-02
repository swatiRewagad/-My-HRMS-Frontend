import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../services/session.service';

/**
 * NFR-005: Auto-logout after configured inactivity period.
 * NFR-013: Ensures auth token is attached and session is valid.
 */
export const sessionTimeoutInterceptor: HttpInterceptorFn = (req, next) => {
  const sessionService = inject(SessionService);
  const router = inject(Router);

  const hasSession = sessionService.getToken() !== null;

  if (hasSession && sessionService.isExpired()) {
    sessionService.clearSession();
    router.navigate(['/public']);
    throw new Error('Session expired');
  }

  if (hasSession) {
    sessionService.resetTimer();
    const token = sessionService.getToken();
    if (token) {
      return next(req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      }));
    }
  }

  return next(req);
};
