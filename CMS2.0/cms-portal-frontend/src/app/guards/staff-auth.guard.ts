import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakAuthService } from '../services/keycloak-auth.service';

export const staffAuthGuard: CanActivateFn = async () => {
  const auth = inject(KeycloakAuthService);
  const router = inject(Router);

  const authenticated = await auth.init();
  if (authenticated) {
    return true;
  }

  router.navigate(['/staff/login']);
  return false;
};

export const staffRoleGuard = (allowedRoles: string[]): CanActivateFn => {
  return async () => {
    const auth = inject(KeycloakAuthService);
    const router = inject(Router);

    const authenticated = await auth.init();
    if (!authenticated) {
      router.navigate(['/staff/login']);
      return false;
    }

    if (auth.hasAnyRole(allowedRoles)) {
      return true;
    }

    router.navigate(['/staff/unauthorized']);
    return false;
  };
};
