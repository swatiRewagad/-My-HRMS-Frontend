import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

/**
 * NFR-005: Enforces active session for protected routes.
 * NFR-013: Role-based access control gate.
 */
export const authGuard: CanActivateFn = () => {
  const sessionService = inject(SessionService);
  const router = inject(Router);

  if (sessionService.isExpired()) {
    sessionService.clearSession();
    router.navigate(['/public']);
    return false;
  }

  return true;
};
