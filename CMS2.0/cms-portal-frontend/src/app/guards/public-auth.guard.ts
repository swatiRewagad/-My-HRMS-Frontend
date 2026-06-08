import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { PublicAuthService } from '../services/public-auth.service';

export const publicAuthGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  const authService = inject(PublicAuthService);
  const router = inject(Router);

  if (authService.isSessionValid()) {
    return true;
  }

  authService.logout();
  router.navigate(['/public/login'], { queryParams: { returnUrl: state.url } });
  return false;
};
