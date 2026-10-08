import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakAuthService } from '../services/keycloak-auth.service';

/**
 * Redirects are returned as UrlTrees rather than performed with `router.navigate()` alongside a
 * `false` return.
 *
 * <p>That older form starts a second navigation from inside the one being evaluated, and the
 * `false` cancels the outer one. The router can drop the nested navigation, which leaves the
 * address bar on the page the user was already looking at with nothing logged — a click that
 * silently does nothing. Returning a UrlTree lets the router perform the redirect itself as part
 * of the same navigation, so the user actually lands on login or unauthorized.
 */
export const staffAuthGuard: CanActivateFn = async () => {
  const auth = inject(KeycloakAuthService);
  const router = inject(Router);

  const authenticated = await auth.init();
  if (authenticated) {
    return true;
  }

  return router.createUrlTree(['/staff/login']);
};

export const staffRoleGuard = (allowedRoles: string[]): CanActivateFn => {
  return async () => {
    const auth = inject(KeycloakAuthService);
    const router = inject(Router);

    const authenticated = await auth.init();
    if (!authenticated) {
      return router.createUrlTree(['/staff/login']);
    }

    if (auth.hasAnyRole(allowedRoles)) {
      return true;
    }

    return router.createUrlTree(['/staff/unauthorized']);
  };
};
