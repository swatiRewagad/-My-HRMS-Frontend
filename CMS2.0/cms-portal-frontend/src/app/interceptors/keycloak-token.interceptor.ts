import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { KeycloakAuthService } from '../services/keycloak-auth.service';

// Anonymous/citizen-facing endpoints — never attach the staff Keycloak token here, even if a
// staff SSO session happens to be active in the same browser (e.g. a leftover session from an
// earlier staff login, or one that's expired mid-way through a long public form). A stale/invalid
// Bearer token on an otherwise-permitAll request still gets rejected with 401 by the gateway's
// resource-server filter, which only skips the *authorization* check, not token validation.
const PUBLIC_API_PATH_PREFIXES = [
  '/api/v1/complaints',
  '/api/v1/eligibility',
  '/api/v1/tat',
  '/api/v1/citizen/auth',
];

export const keycloakTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (PUBLIC_API_PATH_PREFIXES.some(prefix => req.url.includes(prefix))) {
    return next(req);
  }

  const auth = inject(KeycloakAuthService);

  if (!auth.isAuthenticated()) {
    return next(req);
  }

  const token = auth.getToken();
  if (!token) {
    return next(req);
  }

  const cloned = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  });

  return next(cloned);
};
