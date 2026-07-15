import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from './keycloak.service';

export const authGuard: CanActivateFn = () => {
  const keycloak = inject(KeycloakService);
  return keycloak.isAuthenticated;
};

export const adminGuard: CanActivateFn = () => {
  const keycloak = inject(KeycloakService);
  const router = inject(Router);

  if (keycloak.isAdmin) {
    return true;
  }
  router.navigate(['/dashboard']);
  return false;
};
