import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { KeycloakAuthService } from '../services/keycloak-auth.service';

export const keycloakTokenInterceptor: HttpInterceptorFn = (req, next) => {
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
